package shortly.mandmcorp.dev.shortly.service.counter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.dto.request.CounterCashupRequest;
import shortly.mandmcorp.dev.shortly.dto.request.PickedUpRequest;
import shortly.mandmcorp.dev.shortly.dto.response.CounterCashMovementsResponse;
import shortly.mandmcorp.dev.shortly.dto.response.CounterCashupReportRow;
import shortly.mandmcorp.dev.shortly.enums.CounterPaymentMethod;
import shortly.mandmcorp.dev.shortly.enums.DriverAdjustmentType;
import shortly.mandmcorp.dev.shortly.enums.UserRole;
import shortly.mandmcorp.dev.shortly.model.CounterCashup;
import shortly.mandmcorp.dev.shortly.model.CounterCharge;
import shortly.mandmcorp.dev.shortly.model.CounterCollection;
import shortly.mandmcorp.dev.shortly.model.CounterPayment;
import shortly.mandmcorp.dev.shortly.model.DriverAdjustment;
import shortly.mandmcorp.dev.shortly.model.DriverPayout;
import shortly.mandmcorp.dev.shortly.model.Office;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.OfficeRepository;
import shortly.mandmcorp.dev.shortly.utils.BusinessDay;
import shortly.mandmcorp.dev.shortly.utils.CounterPickupRules;
import shortly.mandmcorp.dev.shortly.utils.CurrentUser;

/** Counter pickup payments, the station's daily cash movements and the cash-up (Part L). */
@Service
@Slf4j
public class CounterService {

    private final MongoTemplate mongoTemplate;
    private final OfficeRepository officeRepository;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public CounterService(MongoTemplate mongoTemplate, OfficeRepository officeRepository,
                          org.springframework.transaction.support.TransactionTemplate transactionTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.officeRepository = officeRepository;
        this.transactionTemplate = transactionTemplate;
    }

    // ------------------------------------------------------------------ 2.1 recording a pickup

    /** Throws 409 if a counter payment was already recorded for the parcel. */
    public void assertNotRecorded(String parcelId) {
        if (mongoTemplate.exists(new Query(Criteria.where("parcelId").is(parcelId)), CounterCollection.class)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This parcel's pickup has already been recorded");
        }
    }

    /**
     * Validates and records the counter payment for a parcel being collected. Sets the parcel's
     * pickUpCost, counterPayment and POD flags (the caller saves the parcel). Returns null for old
     * clients that send no payment (amountPaid absent).
     */
    public CounterCollection recordPickup(Parcel parcel, PickedUpRequest request, User user, String officeId) {
        if (request.getAmountPaid() == null) return null;
        assertNotRecorded(parcel.getParcelId());

        double pickupFee = resolvePickupFee(parcel, request.getPickUpCost(), officeId);
        List<CounterCharge> charges = CounterPickupRules.charges(parcel, pickupFee);
        double amountDue = CounterPickupRules.amountDue(charges);
        double amountPaid = CounterPickupRules.round2(request.getAmountPaid());
        double variance = CounterPickupRules.validatePayment(amountDue, request.getAmountDue(), amountPaid,
                request.getVarianceAmount(), request.getPaymentMethod(), request.getMomoReference(),
                request.getVarianceReason(), request.getVarianceNote());
        boolean noVariance = Math.abs(variance) <= 0.01;

        long now = System.currentTimeMillis();
        String stationOffice = officeId != null ? officeId : parcel.getOfficeId();
        CounterCollection collection = CounterCollection.builder()
                .parcelId(parcel.getParcelId())
                .companyId(parcel.getCompanyId())
                .officeId(stationOffice)
                .businessDate(openBusinessDate(stationOffice).toString())
                .pickedUpAt(now)
                .receiverName(parcel.getReceiverName())
                .collectedByName(request.getWhoPickedUpName())
                .collectedByPhone(request.getWhoPickedUpTelephoneNumber())
                .pickUpCost(pickupFee)
                .amountDue(amountDue)
                .amountPaid(amountPaid)
                .varianceAmount(variance)
                .paymentMethod(amountPaid > 0 ? request.getPaymentMethod() : null)
                .momoReference(request.getPaymentMethod() == CounterPaymentMethod.MOMO ? request.getMomoReference() : null)
                .varianceReason(noVariance ? null : request.getVarianceReason())
                .varianceNote(noVariance ? null : request.getVarianceNote())
                .charges(charges)
                .podAmount(parcel.isPOD() ? CounterPickupRules.round2(parcel.getItemCost()) : 0)
                .recordedById(user.getUserId())
                .recordedByName(user.getName())
                .build();
        try {
            collection = mongoTemplate.insert(collection);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This parcel's pickup has already been recorded");
        }

        parcel.setPickUpCost(pickupFee);
        parcel.setCounterPayment(CounterPayment.from(collection));
        if (parcel.isPOD()) {
            parcel.setPodCollected(true);
            parcel.setPodCollectedAt(now);
        }
        return collection;
    }

    /** Fee sent by the counter; else the parcel's own; else the station's charge when switched on. */
    private double resolvePickupFee(Parcel parcel, Double sent, String officeId) {
        if (sent != null) {
            if (sent < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pickUpCost can't be negative");
            return CounterPickupRules.round2(sent);
        }
        if (parcel.getPickUpCost() > 0) return parcel.getPickUpCost();
        String office = officeId != null ? officeId : parcel.getOfficeId();
        if (office == null) return 0;
        return officeRepository.findById(office)
                .filter(o -> Boolean.TRUE.equals(o.getPickupChargeEnabled()) && o.getPickupChargeAmount() != null)
                .map(Office::getPickupChargeAmount)
                .orElse(0.0);
    }

    /** Today, or the next day not yet closed: money taken after closing goes into the next day's drawer. */
    public LocalDate openBusinessDate(String officeId) {
        LocalDate day = BusinessDay.today();
        if (officeId == null) return day;
        for (int i = 0; i < 31 && isClosed(officeId, day); i++) day = day.plusDays(1);
        return day;
    }

    private boolean isClosed(String officeId, LocalDate day) {
        return mongoTemplate.exists(new Query(Criteria.where("officeId").is(officeId).and("date").is(day.toString())),
                CounterCashup.class);
    }

    // ------------------------------------------------------------------ 2.2 / 3.7 the day's list

    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public List<CounterCollection> collections(String date, String officeId) {
        String office = officeFor(officeId);
        return collectionsFor(office, BusinessDay.parse(date));
    }

    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public CounterCashMovementsResponse cashMovements(String date, String officeId) {
        String office = officeFor(officeId);
        LocalDate day = BusinessDay.parse(date);
        return movements(office, day);
    }

    private List<CounterCollection> collectionsFor(String officeId, LocalDate day) {
        Query q = new Query(Criteria.where("companyId").is(CurrentUser.companyId())
                .and("officeId").is(officeId).and("businessDate").is(day.toString()));
        q.with(org.springframework.data.domain.Sort.by("pickedUpAt"));
        return mongoTemplate.find(q, CounterCollection.class);
    }

    private CounterCashMovementsResponse movements(String officeId, LocalDate day) {
        String company = CurrentUser.companyId();
        List<CounterCollection> collections = collectionsFor(officeId, day);
        List<DriverPayout> payouts = mongoTemplate.find(new Query(Criteria.where("companyId").is(company)
                .and("officeId").is(officeId).and("businessDate").is(day.toString())
                .and("paymentMethod").is(CounterPaymentMethod.CASH).and("reversed").is(false)), DriverPayout.class);
        List<DriverAdjustment> advances = mongoTemplate.find(new Query(Criteria.where("companyId").is(company)
                .and("officeId").is(officeId).and("businessDate").is(day.toString())
                .and("type").is(DriverAdjustmentType.ADVANCE).and("paymentMethod").is(CounterPaymentMethod.CASH)),
                DriverAdjustment.class);

        double due = 0, received = 0, cash = 0, momo = 0;
        for (CounterCollection c : collections) {
            due += c.getAmountDue();
            received += c.getAmountPaid();
            if (c.getPaymentMethod() == CounterPaymentMethod.CASH) cash += c.getAmountPaid();
            if (c.getPaymentMethod() == CounterPaymentMethod.MOMO) momo += c.getAmountPaid();
        }
        List<CounterCollection> podCashPayouts = mongoTemplate.find(new Query(Criteria.where("companyId").is(company)
                .and("officeId").is(officeId).and("podPayoutBusinessDate").is(day.toString())
                .and("podPaymentMethod").is(CounterPaymentMethod.CASH)), CounterCollection.class);
        double podCash = podCashPayouts.stream().mapToDouble(CounterCollection::getPodAmount).sum();
        double payoutCash = payouts.stream().mapToDouble(DriverPayout::getNetAmount).sum();
        double advanceCash = advances.stream().mapToDouble(DriverAdjustment::getAmount).sum();

        return CounterCashMovementsResponse.builder()
                .date(day.toString())
                .officeId(officeId)
                .collections(collections)
                .driverCashPayouts(payouts)
                .driverCashAdvances(advances)
                .podCashPayouts(podCashPayouts)
                .totals(CounterCashMovementsResponse.Totals.builder()
                        .count(collections.size())
                        .due(r(due)).received(r(received))
                        .counterCash(r(cash)).momo(r(momo))
                        .shortOver(r(received - due))
                        .driverCashPayouts(r(payoutCash))
                        .driverCashAdvances(r(advanceCash))
                        .podCashPayouts(r(podCash))
                        .expectedCash(r(cash - payoutCash - advanceCash - podCash))
                        .expectedMomo(r(momo))
                        .build())
                .closed(isClosed(officeId, day))
                .build();
    }

    // ------------------------------------------------------------------ 2.3 cash-up

    @PreAuthorize("hasRole('MANAGER')")
    public Map<String, String> closeDay(CounterCashupRequest request) {
        User manager = CurrentUser.get();
        String officeId = CurrentUser.officeId(manager);
        if (officeId == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager has no assigned office");
        if (request.getCountedCash() == null || request.getCountedCash() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "countedCash is required");
        }
        LocalDate day = BusinessDay.parse(request.getDate());
        if (day.isAfter(BusinessDay.today())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can't close a future day");
        }
        if (isClosed(officeId, day)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This day is already closed");
        }

        CounterCashMovementsResponse.Totals t = movements(officeId, day).getTotals();
        double counted = r(request.getCountedCash());
        double difference = r(counted - t.getExpectedCash());
        if (Math.abs(difference) > 0.009 && (request.getNote() == null || request.getNote().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A note is required: counted cash differs from the expected GHC " + String.format("%.2f", t.getExpectedCash()));
        }

        CounterCashup cashup = CounterCashup.builder()
                .companyId(manager.getCompanyId()).officeId(officeId).date(day.toString())
                .expectedCash(t.getExpectedCash()).countedCash(counted).cashDifference(difference)
                .expectedMomo(t.getExpectedMomo()).note(request.getNote())
                .collectionCount(t.getCount()).totalDue(t.getDue()).totalReceived(t.getReceived())
                .counterCash(t.getCounterCash()).driverCashPayouts(t.getDriverCashPayouts())
                .driverCashAdvances(t.getDriverCashAdvances()).podCashPayouts(t.getPodCashPayouts()).shortOver(t.getShortOver())
                .closedAt(System.currentTimeMillis()).closedById(manager.getUserId()).closedByName(manager.getName())
                .build();
        try {
            mongoTemplate.insert(cashup);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This day is already closed");
        }
        return Map.of("message", "Cash-up saved");
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public CounterCashup getCashup(String date, String officeId) {
        String office = officeFor(officeId);
        CounterCashup cashup = mongoTemplate.findOne(new Query(Criteria.where("officeId").is(office)
                .and("date").is(BusinessDay.parse(date).toString())), CounterCashup.class);
        if (cashup == null || !Objects.equals(cashup.getCompanyId(), CurrentUser.companyId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This day hasn't been closed yet");
        }
        return cashup;
    }

    /**
     * Cash that came back into the drawer for a day (a reversed cash payout). An open day picks
     * this up by itself, because reversed payouts drop out of expected cash; a closed day gets a
     * "cash returned" adjustment so the closed figures stay as they were.
     */
    public void cashReturned(String officeId, String date, double amount, String reason, User user) {
        if (officeId == null || date == null || amount <= 0) return;
        CounterCashup closed = mongoTemplate.findOne(new Query(Criteria.where("officeId").is(officeId).and("date").is(date)), CounterCashup.class);
        if (closed == null) return;
        CounterCashup.CashupAdjustment adj = new CounterCashup.CashupAdjustment(r(amount), reason,
                System.currentTimeMillis(), user != null ? user.getUserId() : null, user != null ? user.getName() : null);
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(closed.getId())),
                new Update().push("adjustments", adj), CounterCashup.class);
    }

    /** Corrections to a closed day are appended, never edited in place. */
    @PreAuthorize("hasRole('MANAGER')")
    public CounterCashup addAdjustment(String date, Double amount, String reason) {
        User manager = CurrentUser.get();
        CounterCashup cashup = getCashup(date, null);
        if (amount == null || amount == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount is required");
        if (reason == null || reason.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason is required");
        CounterCashup.CashupAdjustment adj = new CounterCashup.CashupAdjustment(r(amount), reason.trim(),
                System.currentTimeMillis(), manager.getUserId(), manager.getName());
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(cashup.getId())),
                new Update().push("adjustments", adj), CounterCashup.class);
        cashup.getAdjustments().add(adj);
        return cashup;
    }

    // ------------------------------------------------------------------ 2.4 reports

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public List<CounterCashupReportRow> cashupReport(String officeId, String from, String to) {
        String office = CurrentUser.scopedOfficeId(officeId);
        String company = CurrentUser.companyId();
        LocalDate toDay = BusinessDay.parse(to);
        LocalDate fromDay = (from == null || from.isBlank()) ? toDay.minusDays(30) : BusinessDay.parse(from);

        Criteria base = Criteria.where("companyId").is(company);
        if (office != null) base = base.and("officeId").is(office);
        List<CounterCashup> cashups = mongoTemplate.find(new Query(Criteria.where("companyId").is(company)
                .andOperator(office == null ? new Criteria() : Criteria.where("officeId").is(office),
                        Criteria.where("date").gte(fromDay.toString()).lte(toDay.toString()))), CounterCashup.class);
        List<CounterCollection> collections = mongoTemplate.find(new Query(base
                .and("businessDate").gte(fromDay.toString()).lte(toDay.toString())), CounterCollection.class);

        Map<String, CounterCashupReportRow> rows = new TreeMap<>();
        Map<String, String> officeNames = new LinkedHashMap<>();
        for (CounterCollection c : collections) {
            CounterCashupReportRow row = rows.computeIfAbsent(c.getOfficeId() + "|" + c.getBusinessDate(),
                    k -> CounterCashupReportRow.builder().officeId(c.getOfficeId()).date(c.getBusinessDate()).build());
            row.setCollectionCount(row.getCollectionCount() + 1);
        }
        for (CounterCashup cu : cashups) {
            CounterCashupReportRow row = rows.computeIfAbsent(cu.getOfficeId() + "|" + cu.getDate(),
                    k -> CounterCashupReportRow.builder().officeId(cu.getOfficeId()).date(cu.getDate()).build());
            row.setClosed(true);
            row.setExpectedCash(cu.getExpectedCash());
            row.setCountedCash(cu.getCountedCash());
            row.setDifference(cu.getCashDifference());
            row.setExpectedMomo(cu.getExpectedMomo());
            row.setClosedByName(cu.getClosedByName());
            row.setClosedAt(cu.getClosedAt());
            row.setNote(cu.getNote());
            row.setAdjustmentTotal(r(cu.getAdjustments().stream().mapToDouble(CounterCashup.CashupAdjustment::getAmount).sum()));
        }
        List<CounterCashupReportRow> out = new ArrayList<>(rows.values());
        for (CounterCashupReportRow row : out) {
            row.setOfficeName(officeNames.computeIfAbsent(row.getOfficeId(),
                    id -> id == null ? null : officeRepository.findById(id).map(Office::getName).orElse(null)));
        }
        out.sort(Comparator.comparing(CounterCashupReportRow::getDate).reversed()
                .thenComparing(row -> String.valueOf(row.getOfficeName())));
        return out;
    }

    /** POD item values collected at the counter and still owed to senders/vendors. */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public List<CounterCollection> podOwed(String officeId) {
        String office = CurrentUser.scopedOfficeId(officeId);
        Criteria c = Criteria.where("companyId").is(CurrentUser.companyId()).and("podAmount").gt(0).and("podPaidOut").is(false);
        if (office != null) c = c.and("officeId").is(office);
        List<CounterCollection> owed = mongoTemplate.find(new Query(c).with(
                org.springframework.data.domain.Sort.by("pickedUpAt")), CounterCollection.class);
        // Drop ones already paid out through the parcel's itemOwnerPaid flag.
        List<CounterCollection> out = new ArrayList<>();
        for (CounterCollection cc : owed) {
            Parcel p = mongoTemplate.findById(cc.getParcelId(), Parcel.class);
            if (p != null && p.isItemOwnerPaid()) {
                mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(cc.getId())),
                        new Update().set("podPaidOut", true), CounterCollection.class);
            } else {
                out.add(cc);
            }
        }
        return out;
    }

    /** Counter money in a period for the dashboards (company-scoped; officeId null = all stations). */
    public shortly.mandmcorp.dev.shortly.dto.response.CounterDashboardTotals dashboardTotals(String officeId, long start, long end) {
        Criteria c = Criteria.where("companyId").is(CurrentUser.companyId()).and("pickedUpAt").gte(start).lte(end);
        if (officeId != null) c = c.and("officeId").is(officeId);
        double due = 0, received = 0, cash = 0, momo = 0, pod = 0;
        List<CounterCollection> list = mongoTemplate.find(new Query(c), CounterCollection.class);
        for (CounterCollection cc : list) {
            due += cc.getAmountDue();
            received += cc.getAmountPaid();
            pod += cc.getPodAmount();
            if (cc.getPaymentMethod() == CounterPaymentMethod.CASH) cash += cc.getAmountPaid();
            if (cc.getPaymentMethod() == CounterPaymentMethod.MOMO) momo += cc.getAmountPaid();
        }
        Criteria pc = Criteria.where("companyId").is(CurrentUser.companyId()).and("reversed").is(false)
                .and("paidAt").gte(start).lte(end);
        if (officeId != null) pc = pc.and("officeId").is(officeId);
        double payouts = mongoTemplate.find(new Query(pc), DriverPayout.class).stream().mapToDouble(DriverPayout::getNetAmount).sum();
        return shortly.mandmcorp.dev.shortly.dto.response.CounterDashboardTotals.builder()
                .pickups(list.size()).due(r(due)).received(r(received)).cash(r(cash)).momo(r(momo))
                .shortOver(r(received - due)).podCollected(r(pod)).driverPayouts(r(payouts))
                .build();
    }

    // ------------------------------------------------------------------ POD payouts

    /**
     * Records that collected POD values were paid on to the sender/vendor. All or nothing: 409 if
     * any is already paid out. Cash comes out of the station's counter drawer for the day.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public List<CounterCollection> payOutPod(shortly.mandmcorp.dev.shortly.dto.request.PodPayoutRequest req) {
        User user = CurrentUser.get();
        List<String> ids = req.getCollectionIds();
        if (ids == null || ids.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "collectionIds is required");
        if (ids.stream().distinct().count() != ids.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A collection is listed twice");
        if (req.getPaymentMethod() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paymentMethod (CASH or MOMO) is required");
        if (req.getPaymentMethod() == CounterPaymentMethod.MOMO && (req.getMomoReference() == null || req.getMomoReference().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "momoReference is required for mobile money");
        }
        String scoped = CurrentUser.scopedOfficeId(null); // managers: own office only
        try {
            return transactionTemplate.execute(tx -> {
                List<CounterCollection> list = mongoTemplate.find(new Query(Criteria.where("_id").in(ids)), CounterCollection.class);
                if (list.size() != ids.size()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Some collections were not found");
                for (CounterCollection c : list) {
                    if (!Objects.equals(c.getCompanyId(), user.getCompanyId())
                            || (scoped != null && !scoped.equals(c.getOfficeId()))) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Some collections were not found");
                    }
                    if (c.getPodAmount() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parcel " + c.getParcelId() + " has no POD value");
                    if (c.isPodPaidOut()) throw new ResponseStatusException(HttpStatus.CONFLICT, "POD for parcel " + c.getParcelId() + " was already paid out");
                }
                long now = System.currentTimeMillis();
                List<CounterCollection> out = new ArrayList<>();
                for (CounterCollection c : list) {
                    Update u = new Update().set("podPaidOut", true).set("podPaidAt", now)
                            .set("podPaidById", user.getUserId()).set("podPaidByName", user.getName())
                            .set("podPaymentMethod", req.getPaymentMethod())
                            .set("podMomoReference", req.getPaymentMethod() == CounterPaymentMethod.MOMO ? req.getMomoReference().trim() : null)
                            .set("podPaidTo", req.getPaidTo()).set("podPayoutNote", req.getNote())
                            .set("podPayoutBusinessDate", openBusinessDate(c.getOfficeId()).toString());
                    CounterCollection updated = mongoTemplate.findAndModify(
                            new Query(Criteria.where("_id").is(c.getId()).and("podPaidOut").is(false)), u,
                            org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true), CounterCollection.class);
                    if (updated == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "POD for parcel " + c.getParcelId() + " was just paid out");
                    mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(c.getParcelId())),
                            new Update().set("isItemOwnerPaid", true), Parcel.class);
                    out.add(updated);
                }
                return out;
            });
        } catch (org.springframework.dao.DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Someone else paid these out at the same moment; reload and try again");
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Front desk and managers: their own office. Admins: the officeId they pass (required). */
    private String officeFor(String requested) {
        User user = CurrentUser.get();
        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPERADMIN) {
            if (requested == null || requested.isBlank()) {
                String own = CurrentUser.officeId(user);
                if (own == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "officeId is required");
                return own;
            }
            return requested;
        }
        String own = CurrentUser.officeId(user);
        if (own == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have no assigned office");
        return own;
    }

    private static double r(double v) {
        return CounterPickupRules.round2(v);
    }
}
