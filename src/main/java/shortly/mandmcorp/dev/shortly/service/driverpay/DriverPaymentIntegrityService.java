package shortly.mandmcorp.dev.shortly.service.driverpay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.enums.DriverAdjustmentType;
import shortly.mandmcorp.dev.shortly.enums.DriverFeeState;
import shortly.mandmcorp.dev.shortly.model.DriverAdjustment;
import shortly.mandmcorp.dev.shortly.model.DriverAssignment;
import shortly.mandmcorp.dev.shortly.model.DriverPaymentIssue;
import shortly.mandmcorp.dev.shortly.model.DriverPayout;
import shortly.mandmcorp.dev.shortly.utils.CurrentUser;
import shortly.mandmcorp.dev.shortly.utils.Money;

/**
 * Nightly check that driver money adds up: every PAID line has a live payout, payout gross equals
 * its lines, net = gross − deductions + bonuses, no line is on two live payouts, and each advance's
 * repaidAmount equals its repayments on live payouts. Problems go to driver_payment_issues.
 */
@Service
@Slf4j
public class DriverPaymentIntegrityService {

    private final MongoTemplate mongoTemplate;

    public DriverPaymentIntegrityService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Scheduled(cron = "${shortly.integrity.cron:0 30 2 * * *}", zone = "Africa/Accra")
    public void nightly() {
        try {
            List<String> companies = mongoTemplate.findDistinct(new Query(), "companyId", DriverPayout.class, String.class);
            List<String> fromLines = mongoTemplate.findDistinct(new Query(Criteria.where("state").is(DriverFeeState.PAID)),
                    "companyId", DriverAssignment.class, String.class);
            java.util.Set<String> all = new java.util.LinkedHashSet<>(companies);
            all.addAll(fromLines);
            if (all.isEmpty()) all.add(null);
            int total = 0;
            for (String c : all) total += check(c).size();
            log.info("Driver payments integrity check: {} problem(s)", total);
        } catch (RuntimeException e) {
            log.error("Driver payments integrity check failed", e);
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'MANAGER')")
    public List<DriverPaymentIssue> issues(String officeId) {
        String office = CurrentUser.scopedOfficeId(officeId);
        Criteria c = Criteria.where("companyId").is(CurrentUser.companyId());
        if (office != null) c = c.and("officeId").is(office);
        return mongoTemplate.find(new Query(c).with(Sort.by("type", "officeId")), DriverPaymentIssue.class);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public List<DriverPaymentIssue> runNow() {
        return check(CurrentUser.companyId());
    }

    /** Checks one company and replaces its stored issues. */
    List<DriverPaymentIssue> check(String companyId) {
        long now = System.currentTimeMillis();
        List<DriverPaymentIssue> found = new ArrayList<>();
        Criteria company = Criteria.where("companyId").is(companyId);

        List<DriverPayout> payouts = mongoTemplate.find(new Query(company), DriverPayout.class);
        Map<String, DriverPayout> byId = new HashMap<>();
        Map<String, List<String>> livePayoutsByLine = new HashMap<>();
        for (DriverPayout p : payouts) {
            byId.put(p.getId(), p);
            if (p.isReversed()) continue;
            for (String lineId : p.getLineIds()) livePayoutsByLine.computeIfAbsent(lineId, k -> new ArrayList<>()).add(p.getId());
        }

        // 1. every PAID line has a non-reversed payout
        List<DriverAssignment> paid = mongoTemplate.find(new Query(Criteria.where("companyId").is(companyId)
                .and("state").is(DriverFeeState.PAID)), DriverAssignment.class);
        Map<String, Long> linesSumByPayout = new HashMap<>();
        for (DriverAssignment l : paid) {
            DriverPayout p = l.getPayoutId() == null ? null : byId.get(l.getPayoutId());
            if (p == null || p.isReversed()) {
                found.add(issue(companyId, l.getOfficeId(), "PAID_LINE_WITHOUT_PAYOUT",
                        "Fee for parcel " + l.getParcelId() + " is PAID but " + (p == null ? "has no payout" : "its payout " + p.getReceiptNo() + " was reversed"),
                        l.getId(), now));
            } else {
                linesSumByPayout.merge(p.getId(), Money.p(l.getAmount()), Long::sum);
            }
        }

        for (DriverPayout p : payouts) {
            if (p.isReversed()) continue;
            // 2. gross equals its lines
            long lines = linesSumByPayout.getOrDefault(p.getId(), 0L);
            if (lines != Money.p(p.getGrossAmount())) {
                found.add(issue(companyId, p.getOfficeId(), "GROSS_MISMATCH",
                        "Payout " + p.getReceiptNo() + " gross GHC " + Money.format(Money.p(p.getGrossAmount()))
                                + " but its paid fees add up to GHC " + Money.format(lines), p.getId(), now));
            }
            // 3. net = gross − deductions + bonuses
            long net = Money.p(p.getGrossAmount()) - Money.p(p.getDeductions()) + Money.p(p.getBonuses());
            if (net != Money.p(p.getNetAmount())) {
                found.add(issue(companyId, p.getOfficeId(), "NET_MISMATCH",
                        "Payout " + p.getReceiptNo() + " net GHC " + Money.format(Money.p(p.getNetAmount()))
                                + " should be GHC " + Money.format(net), p.getId(), now));
            }
        }

        // 4. no line on two live payouts
        livePayoutsByLine.forEach((lineId, ids) -> {
            if (ids.size() > 1) {
                DriverPayout first = byId.get(ids.get(0));
                found.add(issue(companyId, first.getOfficeId(), "LINE_ON_TWO_PAYOUTS",
                        "A fee is on " + ids.size() + " payouts: " + ids.stream().map(id -> byId.get(id).getReceiptNo()).toList(),
                        lineId, now));
            }
        });

        // 5. each advance's repaidAmount equals its repayments on live payouts
        Map<String, Long> repaidByAdvance = new HashMap<>();
        for (DriverPayout p : payouts) {
            if (p.isReversed()) continue;
            for (DriverPayout.DriverPayoutAdjustment a : p.getAdjustments()) {
                if (a.getType() == DriverAdjustmentType.ADVANCE_REPAID && a.getAdjustmentId() != null) {
                    repaidByAdvance.merge(a.getAdjustmentId(), Money.p(a.getAmount()), Long::sum);
                }
            }
        }
        for (DriverAdjustment adv : mongoTemplate.find(new Query(Criteria.where("companyId").is(companyId)
                .and("type").is(DriverAdjustmentType.ADVANCE)), DriverAdjustment.class)) {
            long expected = repaidByAdvance.getOrDefault(adv.getId(), 0L);
            if (expected != Money.p(adv.getRepaidAmount())) {
                found.add(issue(companyId, adv.getOfficeId(), "ADVANCE_REPAID_MISMATCH",
                        "Advance \"" + adv.getReason() + "\" shows GHC " + Money.format(Money.p(adv.getRepaidAmount()))
                                + " repaid, but payouts recovered GHC " + Money.format(expected), adv.getId(), now));
            }
        }

        mongoTemplate.remove(new Query(Criteria.where("companyId").is(companyId)), DriverPaymentIssue.class);
        if (!found.isEmpty()) mongoTemplate.insertAll(found);
        return found;
    }

    private static DriverPaymentIssue issue(String company, String office, String type, String detail, String ref, long now) {
        return DriverPaymentIssue.builder().companyId(company).officeId(office).type(type).detail(detail).refId(ref).checkedAt(now).build();
    }
}
