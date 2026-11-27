package shortly.mandmcorp.dev.shortly.service.driverpay;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.dto.request.DriverPaymentRequests.AdjustmentRequest;
import shortly.mandmcorp.dev.shortly.dto.request.DriverPaymentRequests.DriverRequest;
import shortly.mandmcorp.dev.shortly.dto.request.DriverPaymentRequests.PayoutAdjustment;
import shortly.mandmcorp.dev.shortly.dto.request.DriverPaymentRequests.PayoutRequest;
import shortly.mandmcorp.dev.shortly.dto.request.DriverPaymentRequests.TripRequest;
import shortly.mandmcorp.dev.shortly.dto.response.DriverPaymentViews;
import shortly.mandmcorp.dev.shortly.dto.response.DriverPaymentViews.BalanceRow;
import shortly.mandmcorp.dev.shortly.dto.response.DriverPaymentViews.Balances;
import shortly.mandmcorp.dev.shortly.dto.response.DriverPaymentViews.FeeLine;
import shortly.mandmcorp.dev.shortly.dto.response.DriverPaymentViews.PayoutTotals;
import shortly.mandmcorp.dev.shortly.dto.response.DriverPaymentViews.Statement;
import shortly.mandmcorp.dev.shortly.enums.CounterPaymentMethod;
import shortly.mandmcorp.dev.shortly.enums.DriverAdjustmentType;
import shortly.mandmcorp.dev.shortly.enums.DriverFeeState;
import shortly.mandmcorp.dev.shortly.enums.DriverStatus;
import shortly.mandmcorp.dev.shortly.enums.UserRole;
import shortly.mandmcorp.dev.shortly.model.Counter;
import shortly.mandmcorp.dev.shortly.model.Driver;
import shortly.mandmcorp.dev.shortly.model.DriverAdjustment;
import shortly.mandmcorp.dev.shortly.model.DriverAssignment;
import shortly.mandmcorp.dev.shortly.model.DriverPayout;
import shortly.mandmcorp.dev.shortly.model.DriverTrip;
import shortly.mandmcorp.dev.shortly.model.Office;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.OfficeRepository;
import shortly.mandmcorp.dev.shortly.service.counter.CounterService;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationInterface;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationRequestTemplate;
import shortly.mandmcorp.dev.shortly.utils.BusinessDay;
import shortly.mandmcorp.dev.shortly.utils.CounterPickupRules;
import shortly.mandmcorp.dev.shortly.utils.CurrentUser;
import shortly.mandmcorp.dev.shortly.utils.PhoneNumberUtils;

/**
 * Driver payments (Part M): driver records, trips, fee-line holds, adjustments, balances,
 * statements, payouts with receipts and SMS confirmation, reversals and reports.
 *
 * Payouts claim their lines with a conditional update (state READY -> PAID), so two staff can't
 * pay the same line; if any line is no longer READY the claim is undone and the call fails (409).
 */
@Service
@Slf4j
public class DriverPaymentService {

    private static final long CODE_TTL_MS = 30 * 60_000L;
    private static final List<DriverFeeState> OPEN_STATES =
            List.of(DriverFeeState.WAITING, DriverFeeState.READY, DriverFeeState.ON_HOLD);

    private final MongoTemplate mongoTemplate;
    private final OfficeRepository officeRepository;
    private final DriverFeeLineService feeLines;
    private final CounterService counterService;
    private final NotificationInterface notification;
    private final SecureRandom random = new SecureRandom();

    public DriverPaymentService(MongoTemplate mongoTemplate, OfficeRepository officeRepository,
                                DriverFeeLineService feeLines, CounterService counterService,
                                @Qualifier("smsNotification") NotificationInterface notification) {
        this.mongoTemplate = mongoTemplate;
        this.officeRepository = officeRepository;
        this.feeLines = feeLines;
        this.counterService = counterService;
        this.notification = notification;
    }

    // ================================================================== 3.1 drivers

    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public Page<Driver> searchDrivers(String search, int page, int size) {
        Criteria c = Criteria.where("companyId").is(CurrentUser.companyId());
        String office = officeOrNull(null);
        if (office != null) c = c.and("officeIds").is(office);
        if (search != null && !search.isBlank()) {
            String term = java.util.regex.Pattern.quote(search.trim());
            String phone = PhoneNumberUtils.normalizeGhana(search);
            c = c.orOperator(
                    Criteria.where("name").regex(term, "i"),
                    Criteria.where("phoneNumbers").regex(term, "i"),
                    Criteria.where("phoneNumbers").is(phone),
                    Criteria.where("vehicles").regex(term, "i"),
                    Criteria.where("company").regex(term, "i"));
        }
        Query q = new Query(c);
        long total = mongoTemplate.count(q, Driver.class);
        q.with(Sort.by("name")).skip((long) page * size).limit(size);
        return new PageImpl<>(mongoTemplate.find(q, Driver.class), PageRequest.of(page, size), total);
    }

    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public Driver createDriver(DriverRequest req) {
        User user = CurrentUser.get();
        List<String> phones = normalizePhones(req.getPhoneNumbers());
        if (req.getName() == null || req.getName().isBlank()) throw bad("name is required");
        if (phones.isEmpty()) throw bad("At least one phone number is required");
        assertPhonesFree(phones, null);
        String office = CurrentUser.officeId(user);
        Driver driver = Driver.builder()
                .companyId(user.getCompanyId())
                .name(req.getName().trim())
                .phoneNumbers(phones)
                .momoNumber(req.getMomoNumber() != null ? PhoneNumberUtils.normalizeGhana(req.getMomoNumber()) : phones.get(0))
                .momoNetwork(req.getMomoNetwork())
                .vehicles(normalizeVehicles(req.getVehicles()))
                .company(req.getCompany())
                .status(req.getStatus() != null ? req.getStatus() : DriverStatus.ACTIVE)
                .officeIds(req.getOfficeIds() != null && !req.getOfficeIds().isEmpty()
                        ? new ArrayList<>(req.getOfficeIds())
                        : office != null ? new ArrayList<>(List.of(office)) : new ArrayList<>())
                .build();
        return mongoTemplate.save(driver);
    }

    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public Driver updateDriver(String id, DriverRequest req) {
        Driver driver = requireDriver(id);
        if (req.getName() != null && !req.getName().isBlank()) driver.setName(req.getName().trim());
        if (req.getPhoneNumbers() != null) {
            List<String> phones = normalizePhones(req.getPhoneNumbers());
            if (phones.isEmpty()) throw bad("At least one phone number is required");
            assertPhonesFree(phones, driver.getId());
            driver.setPhoneNumbers(phones);
        }
        if (req.getMomoNumber() != null) driver.setMomoNumber(PhoneNumberUtils.normalizeGhana(req.getMomoNumber()));
        if (req.getMomoNetwork() != null) driver.setMomoNetwork(req.getMomoNetwork());
        if (req.getVehicles() != null) driver.setVehicles(normalizeVehicles(req.getVehicles()));
        if (req.getCompany() != null) driver.setCompany(req.getCompany());
        if (req.getStatus() != null) driver.setStatus(req.getStatus());
        if (req.getOfficeIds() != null && !req.getOfficeIds().isEmpty()) driver.setOfficeIds(new ArrayList<>(req.getOfficeIds()));
        Driver saved = mongoTemplate.save(driver);
        // keep the name on open trips in step
        mongoTemplate.updateMulti(new Query(Criteria.where("driverId").is(id)),
                new Update().set("driverName", saved.getName()), DriverTrip.class);
        return saved;
    }

    // ================================================================== 3.2 trips

    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public DriverTrip createTrip(TripRequest req) {
        User user = CurrentUser.get();
        Driver driver = requireDriver(req.getDriverId());
        if (driver.getStatus() == DriverStatus.BLOCKED) throw bad("This driver is blocked");
        String office = CurrentUser.officeId(user);
        if (office == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have no assigned office");
        if (req.getDeclaredCount() != null && req.getDeclaredCount() < 0) throw bad("declaredCount can't be negative");
        String vehicle = req.getVehicleNumber() == null || req.getVehicleNumber().isBlank() ? null
                : req.getVehicleNumber().trim().toUpperCase();
        if (vehicle != null && !driver.getVehicles().contains(vehicle)) {
            driver.getVehicles().add(vehicle);
            mongoTemplate.save(driver);
        }
        return mongoTemplate.save(DriverTrip.builder()
                .companyId(user.getCompanyId()).officeId(office)
                .driverId(driver.getId()).driverName(driver.getName())
                .vehicleNumber(vehicle).origin(req.getOrigin())
                .arrivedAt(System.currentTimeMillis()).businessDate(BusinessDay.today().toString())
                .receivedById(user.getUserId()).receivedByName(user.getName())
                .declaredCount(req.getDeclaredCount()).note(req.getNote())
                .build());
    }

    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public List<DriverPaymentViews.Trip> listTrips(String driverId, String from, String to) {
        long[] range = range(from, to, 30);
        Criteria c = Criteria.where("companyId").is(CurrentUser.companyId())
                .and("arrivedAt").gte(range[0]).lt(range[1]);
        String office = officeOrNull(null);
        if (office != null) c = c.and("officeId").is(office);
        if (driverId != null && !driverId.isBlank()) c = c.and("driverId").is(driverId);
        List<DriverTrip> trips = mongoTemplate.find(new Query(c).with(Sort.by(Sort.Direction.DESC, "arrivedAt")), DriverTrip.class);
        Map<String, List<DriverAssignment>> linesByTrip = linesForTrips(trips.stream().map(DriverTrip::getId).toList());
        return trips.stream().map(t -> tripView(t, linesByTrip.getOrDefault(t.getId(), List.of()), false)).toList();
    }

    // ================================================================== 3.3 / 3.6 holds

    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public DriverAssignment hold(String lineId, String reason) {
        if (reason == null || reason.isBlank()) throw bad("A reason is required");
        DriverAssignment line = requireLine(lineId);
        DriverFeeState state = DriverFeeLineService.stateOf(line);
        if (state != DriverFeeState.WAITING && state != DriverFeeState.READY) {
            throw conflict("Only waiting or ready fees can be put on hold (this one is " + state + ")");
        }
        User user = CurrentUser.get();
        Query q = new Query(Criteria.where("_id").is(lineId).and("payed").is(false)
                .orOperator(Criteria.where("state").is(state), Criteria.where("state").exists(false)));
        DriverAssignment updated = mongoTemplate.findAndModify(q, new Update()
                .set("state", DriverFeeState.ON_HOLD).set("stateBeforeHold", state)
                .set("holdReason", reason.trim()).set("heldBy", user.getName()).set("heldAt", System.currentTimeMillis()),
                FindAndModifyOptions.options().returnNew(true), DriverAssignment.class);
        if (updated == null) throw conflict("This fee changed while you were holding it; reload and try again");
        return updated;
    }

    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public DriverAssignment release(String lineId) {
        DriverAssignment line = requireLine(lineId);
        if (line.getState() != DriverFeeState.ON_HOLD) throw conflict("This fee isn't on hold");
        DriverFeeState back = line.getStateBeforeHold() != null ? line.getStateBeforeHold() : DriverFeeState.WAITING;
        DriverAssignment updated = mongoTemplate.findAndModify(
                new Query(Criteria.where("_id").is(lineId).and("state").is(DriverFeeState.ON_HOLD)),
                new Update().set("state", back).unset("stateBeforeHold").unset("holdReason").unset("heldBy").unset("heldAt"),
                FindAndModifyOptions.options().returnNew(true), DriverAssignment.class);
        if (updated == null) throw conflict("This fee changed; reload and try again");
        return updated;
    }

    // ================================================================== 3.6 adjustments

    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public DriverAdjustment addAdjustment(String driverId, AdjustmentRequest req) {
        Driver driver = requireDriver(driverId);
        User user = CurrentUser.get();
        if (req.getType() == null || req.getType() == DriverAdjustmentType.ADVANCE_REPAID) {
            throw bad("type must be ADVANCE, DEDUCTION or BONUS");
        }
        if (req.getAmount() == null || req.getAmount() <= 0) throw bad("amount must be greater than 0");
        if (req.getReason() == null || req.getReason().isBlank()) throw bad("A reason is required");
        String office = CurrentUser.officeId(user);
        CounterPaymentMethod method = req.getType() == DriverAdjustmentType.ADVANCE
                ? (req.getPaymentMethod() != null ? req.getPaymentMethod() : CounterPaymentMethod.CASH) : null;
        return mongoTemplate.save(DriverAdjustment.builder()
                .companyId(user.getCompanyId()).officeId(office).driverId(driver.getId())
                .type(req.getType()).amount(r(req.getAmount())).reason(req.getReason().trim())
                .parcelId(req.getParcelId()).paymentMethod(method)
                .businessDate(counterService.openBusinessDate(office).toString())
                .open(true).createdAt(System.currentTimeMillis())
                .createdById(user.getUserId()).createdByName(user.getName())
                .build());
    }

    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public List<DriverAdjustment> adjustments(String driverId, Boolean open) {
        requireDriver(driverId);
        Criteria c = Criteria.where("driverId").is(driverId).and("companyId").is(CurrentUser.companyId());
        if (open != null) c = c.and("open").is(open);
        return mongoTemplate.find(new Query(c).with(Sort.by(Sort.Direction.DESC, "createdAt")), DriverAdjustment.class);
    }

    // ================================================================== 3.4 balances, statements, summary

    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public Page<BalanceRow> balances(String search, String state, String officeId, int page, int size) {
        String office = officeOrNull(officeId);
        String company = CurrentUser.companyId();
        Map<String, Balances> byDriver = balancesByDriver(company, office, null);
        Map<String, Long> lastTrips = lastTripByDriver(company, office);

        Criteria dc = Criteria.where("companyId").is(company);
        Set<String> ids = new java.util.HashSet<>(byDriver.keySet());
        if (office != null) {
            dc = dc.orOperator(Criteria.where("_id").in(toObjectIds(ids)), Criteria.where("_id").in(ids),
                    Criteria.where("officeIds").is(office));
        }
        List<Driver> drivers = mongoTemplate.find(new Query(dc), Driver.class);

        String term = search == null ? null : search.trim().toLowerCase();
        String phoneTerm = PhoneNumberUtils.normalizeGhana(search);
        String filter = state == null ? "READY" : state.toUpperCase();
        List<BalanceRow> rows = new ArrayList<>();
        for (Driver d : drivers) {
            if (term != null && !term.isEmpty() && !matches(d, term, phoneTerm)) continue;
            Balances b = byDriver.getOrDefault(d.getId(), new Balances());
            if ("READY".equals(filter) && b.getReadyAmount() <= 0) continue;
            if ("ON_HOLD".equals(filter) && b.getOnHoldAmount() <= 0) continue;
            rows.add(BalanceRow.builder()
                    .driverId(d.getId()).name(d.getName())
                    .phone(d.getPhoneNumbers().isEmpty() ? null : d.getPhoneNumbers().get(0))
                    .vehicles(d.getVehicles()).company(d.getCompany())
                    .readyAmount(b.getReadyAmount()).readyCount(b.getReadyCount())
                    .waitingAmount(b.getWaitingAmount()).onHoldAmount(b.getOnHoldAmount())
                    .paidLast30Days(b.getPaidLast30Days()).lastTripAt(lastTrips.get(d.getId()))
                    .build());
        }
        rows.sort(Comparator.comparingDouble(BalanceRow::getReadyAmount).reversed()
                .thenComparing(BalanceRow::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        int fromIdx = Math.min(page * size, rows.size());
        return new PageImpl<>(rows.subList(fromIdx, Math.min(fromIdx + size, rows.size())), PageRequest.of(page, size), rows.size());
    }

    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public Statement statement(String driverId, String from, String to) {
        Driver driver = requireDriver(driverId);
        String company = CurrentUser.companyId();
        String office = officeOrNull(null);
        long[] range = range(from, to, 30);

        Criteria tripC = Criteria.where("driverId").is(driverId).and("companyId").is(company);
        if (office != null) tripC = tripC.and("officeId").is(office);
        // Trips in the period, plus older trips that still have unpaid fees.
        Criteria openLineC = Criteria.where("driverId").is(driverId).and("companyId").is(company).and("state").in(OPEN_STATES);
        if (office != null) openLineC = openLineC.and("officeId").is(office);
        List<DriverAssignment> openLines = mongoTemplate.find(new Query(openLineC), DriverAssignment.class);
        Set<String> openTripIds = openLines.stream().map(DriverAssignment::getTripId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<DriverTrip> trips = mongoTemplate.find(new Query(tripC.orOperator(
                Criteria.where("arrivedAt").gte(range[0]).lt(range[1]),
                Criteria.where("_id").in(openTripIds))).with(Sort.by(Sort.Direction.DESC, "arrivedAt")), DriverTrip.class);

        Map<String, List<DriverAssignment>> linesByTrip = linesForTrips(trips.stream().map(DriverTrip::getId).toList());
        List<DriverPaymentViews.Trip> tripViews = new ArrayList<>(trips.stream()
                .map(t -> tripView(t, linesByTrip.getOrDefault(t.getId(), List.of()), true)).toList());
        List<DriverAssignment> noTrip = openLines.stream().filter(l -> l.getTripId() == null).toList();
        if (!noTrip.isEmpty()) {
            tripViews.add(DriverPaymentViews.Trip.builder()
                    .driverId(driverId).driverName(driver.getName())
                    .note("Parcels registered before trips were recorded")
                    .receivedCount(noTrip.size())
                    .feeTotal(r(noTrip.stream().mapToDouble(DriverAssignment::getAmount).sum()))
                    .readyAmount(r(noTrip.stream().filter(l -> l.getState() == DriverFeeState.READY).mapToDouble(DriverAssignment::getAmount).sum()))
                    .lines(noTrip.stream().map(this::lineView).toList())
                    .build());
        }

        Criteria pc = Criteria.where("driverId").is(driverId).and("companyId").is(company).and("paidAt").gte(range[0]).lt(range[1]);
        List<DriverPayout> payouts = mongoTemplate.find(new Query(pc).with(Sort.by(Sort.Direction.DESC, "paidAt")), DriverPayout.class);

        return Statement.builder()
                .driver(driver)
                .balances(balancesByDriver(company, office, driverId).getOrDefault(driverId, new Balances()))
                .trips(tripViews)
                .payouts(payouts)
                .openAdjustments(adjustments(driverId, true))
                .build();
    }

    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public DriverPaymentViews.Summary summary(String officeId) {
        String office = officeOrNull(officeId);
        String company = CurrentUser.companyId();
        Map<String, Balances> byDriver = balancesByDriver(company, office, null);
        double ready = 0, waiting = 0, hold = 0;
        int readyDrivers = 0, waitingCount = 0, holdCount = 0;
        for (Balances b : byDriver.values()) {
            ready += b.getReadyAmount();
            waiting += b.getWaitingAmount();
            hold += b.getOnHoldAmount();
            if (b.getReadyAmount() > 0) readyDrivers++;
            waitingCount += b.getWaitingCount();
            holdCount += b.getOnHoldCount();
        }
        LocalDate monday = BusinessDay.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Criteria pc = Criteria.where("companyId").is(company).and("reversed").is(false)
                .and("paidAt").gte(BusinessDay.startMs(monday));
        if (office != null) pc = pc.and("officeId").is(office);
        List<DriverPayout> week = mongoTemplate.find(new Query(pc), DriverPayout.class);
        double paid = week.stream().mapToDouble(DriverPayout::getNetAmount).sum();
        double momo = week.stream().filter(p -> p.getPaymentMethod() == CounterPaymentMethod.MOMO)
                .mapToDouble(DriverPayout::getNetAmount).sum();
        return DriverPaymentViews.Summary.builder()
                .readyAmount(r(ready)).readyDrivers(readyDrivers)
                .waitingAmount(r(waiting)).waitingParcels(waitingCount)
                .paidThisWeekAmount(r(paid)).paidThisWeekCount(week.size())
                .paidThisWeekMomoPercent(paid > 0 ? Math.round(momo / paid * 100) : 0)
                .onHoldAmount(r(hold)).onHoldCount(holdCount)
                .build();
    }

    // ================================================================== 3.5 payouts

    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public DriverPayout createPayout(PayoutRequest req) {
        User user = CurrentUser.get();
        Driver driver = requireDriver(req.getDriverId());
        if (req.getLineIds() == null || req.getLineIds().isEmpty()) throw bad("Select at least one fee to pay");
        List<String> lineIds = req.getLineIds().stream().distinct().toList();
        if (req.getPaymentMethod() == null) throw bad("paymentMethod (CASH or MOMO) is required");
        if (req.getPaymentMethod() == CounterPaymentMethod.MOMO && isBlank(req.getMomoReference())) {
            throw bad("momoReference is required for mobile money");
        }

        List<DriverAssignment> lines = mongoTemplate.find(new Query(Criteria.where("_id").in(lineIds)), DriverAssignment.class);
        if (lines.size() != lineIds.size()) throw bad("Some fees were not found");
        Set<String> offices = new java.util.HashSet<>();
        for (DriverAssignment l : lines) {
            if (!Objects.equals(l.getCompanyId(), user.getCompanyId())) throw bad("Some fees were not found");
            if (!driver.getId().equals(l.getDriverId())) throw bad("Fee " + l.getId() + " belongs to another driver");
            if (l.getState() != DriverFeeState.READY) throw conflict("Fee for parcel " + l.getParcelId() + " is not ready to pay");
            offices.add(l.getOfficeId());
        }
        if (offices.size() > 1) throw bad("Pay one station's fees at a time");
        String office = offices.iterator().next();
        if (user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.SUPERADMIN
                && !Objects.equals(office, CurrentUser.officeId(user))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "These fees belong to another station");
        }

        double gross = r(lines.stream().mapToDouble(DriverAssignment::getAmount).sum());
        List<DriverPayout.Adjustment> applied = new ArrayList<>();
        List<DriverAdjustment> toSettle = new ArrayList<>();
        double deductions = 0, bonuses = 0;
        for (PayoutAdjustment a : req.getAdjustments() == null ? List.<PayoutAdjustment>of() : req.getAdjustments()) {
            if (a.getType() == null || a.getType() == DriverAdjustmentType.ADVANCE) {
                throw bad("Adjustment type must be DEDUCTION, BONUS or ADVANCE_REPAID");
            }
            if (a.getAmount() == null || a.getAmount() <= 0) throw bad("Adjustment amounts must be greater than 0");
            if (isBlank(a.getReason()) && a.getAdjustmentId() == null) throw bad("Each adjustment needs a reason");
            double amount = r(a.getAmount());
            if (a.getAdjustmentId() != null) {
                DriverAdjustment open = mongoTemplate.findById(a.getAdjustmentId(), DriverAdjustment.class);
                if (open == null || !open.isOpen() || !driver.getId().equals(open.getDriverId())) {
                    throw bad("Adjustment " + a.getAdjustmentId() + " is not open for this driver");
                }
                DriverAdjustmentType expected = a.getType() == DriverAdjustmentType.ADVANCE_REPAID ? DriverAdjustmentType.ADVANCE : a.getType();
                if (open.getType() != expected) throw bad("Adjustment " + a.getAdjustmentId() + " is a " + open.getType());
                double outstanding = r(open.getAmount() - open.getRepaidAmount());
                if (amount > outstanding + 0.001) throw bad("Only GHC " + outstanding + " is outstanding on adjustment " + open.getId());
                toSettle.add(open);
                if (isBlank(a.getReason())) a.setReason(open.getReason());
            }
            if (a.getType() == DriverAdjustmentType.BONUS) bonuses += amount; else deductions += amount;
            applied.add(DriverPayout.Adjustment.builder().type(a.getType()).amount(amount).reason(a.getReason())
                    .parcelId(a.getParcelId()).adjustmentId(a.getAdjustmentId()).build());
        }
        double net = r(gross - deductions + bonuses);
        if (net < 0) throw bad("Deductions are larger than the fees: net would be GHC " + net);

        // Claim the lines atomically.
        String payoutId = new ObjectId().toHexString();
        long now = System.currentTimeMillis();
        long claimed = mongoTemplate.updateMulti(new Query(Criteria.where("_id").in(lineIds)
                        .and("driverId").is(driver.getId()).and("state").is(DriverFeeState.READY)),
                new Update().set("state", DriverFeeState.PAID).set("payoutId", payoutId).set("paidAt", now)
                        .set("payed", true).set("whoPayedDriverName", user.getName())
                        .set("whoPayedDriverPhoneNumber", user.getPhoneNumber()),
                DriverAssignment.class).getModifiedCount();
        if (claimed != lineIds.size()) {
            unclaim(payoutId);
            throw conflict("Some of these fees were just paid or changed; reload and try again");
        }

        DriverPayout payout;
        try {
            payout = mongoTemplate.insert(DriverPayout.builder()
                    .id(payoutId)
                    .receiptNo(nextReceiptNo(office))
                    .companyId(user.getCompanyId()).officeId(office)
                    .driverId(driver.getId()).driverName(driver.getName())
                    .driverPhone(driver.getPhoneNumbers().isEmpty() ? null : driver.getPhoneNumbers().get(0))
                    .lineIds(new ArrayList<>(lineIds))
                    .tripCount((int) lines.stream().map(DriverAssignment::getTripId).distinct().count())
                    .adjustments(applied)
                    .grossAmount(gross).deductions(r(deductions)).bonuses(r(bonuses)).netAmount(net)
                    .paymentMethod(req.getPaymentMethod())
                    .momoNumber(req.getPaymentMethod() == CounterPaymentMethod.MOMO
                            ? (isBlank(req.getMomoNumber()) ? driver.getMomoNumber() : PhoneNumberUtils.normalizeGhana(req.getMomoNumber()))
                            : null)
                    .momoReference(req.getPaymentMethod() == CounterPaymentMethod.MOMO ? req.getMomoReference().trim() : null)
                    .note(req.getNote())
                    .businessDate(counterService.openBusinessDate(office).toString())
                    .paidById(user.getUserId()).paidByName(user.getName()).paidAt(now)
                    .build());
        } catch (RuntimeException e) {
            unclaim(payoutId);
            throw e;
        }

        // Settle the adjustments this payout applied.
        for (int i = 0; i < toSettle.size(); i++) {
            DriverAdjustment adj = toSettle.get(i);
            double amount = applied.stream().filter(x -> adj.getId().equals(x.getAdjustmentId()))
                    .mapToDouble(DriverPayout.Adjustment::getAmount).sum();
            double repaid = r(adj.getRepaidAmount() + amount);
            boolean stillOpen = adj.getType() == DriverAdjustmentType.ADVANCE && repaid < adj.getAmount() - 0.001;
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(adj.getId())), new Update()
                    .set("repaidAmount", repaid).set("open", stillOpen)
                    .set("appliedPayoutId", payoutId).set("appliedAt", now), DriverAdjustment.class);
        }
        return withBalanceAfter(payout);
    }

    private void unclaim(String payoutId) {
        mongoTemplate.updateMulti(new Query(Criteria.where("payoutId").is(payoutId)),
                new Update().set("state", DriverFeeState.READY).unset("payoutId").unset("paidAt").set("payed", false),
                DriverAssignment.class);
    }

    private String nextReceiptNo(String officeId) {
        Counter counter = mongoTemplate.findAndModify(
                new Query(Criteria.where("_id").is("driver_payout_" + officeId)),
                new Update().inc("seq", 1),
                FindAndModifyOptions.options().returnNew(true).upsert(true), Counter.class);
        return String.format("DP-%05d", counter.getSeq());
    }

    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public Map<String, String> sendCode(String payoutId) {
        DriverPayout payout = requirePayout(payoutId);
        if (payout.isReversed()) throw conflict("This payout was reversed");
        if (payout.isConfirmed()) throw conflict("This payout is already confirmed");
        String to = payout.getPaymentMethod() == CounterPaymentMethod.MOMO && payout.getMomoNumber() != null
                ? payout.getMomoNumber() : payout.getDriverPhone();
        if (to == null) throw bad("The driver has no phone number");
        String code = String.format("%04d", random.nextInt(10_000));
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(payoutId)),
                new Update().set("confirmationCode", code).set("confirmationCodeSentAt", System.currentTimeMillis()),
                DriverPayout.class);
        notification.send(NotificationRequestTemplate.builder().to(to).otp(true)
                .body("Hello " + payout.getDriverName() + ", you have been paid GHC "
                        + String.format("%.2f", payout.getNetAmount()) + " (receipt " + payout.getReceiptNo()
                        + "). Give this code to the station only once you have the money: " + code)
                .build());
        return Map.of("message", "Code sent", "to", mask(to));
    }

    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public DriverPayout confirm(String payoutId, String code) {
        DriverPayout payout = requirePayout(payoutId);
        if (payout.isConfirmed()) return payout;
        if (payout.getConfirmationCode() == null || payout.getConfirmationCodeSentAt() == null
                || System.currentTimeMillis() - payout.getConfirmationCodeSentAt() > CODE_TTL_MS) {
            throw bad("Send a new code first");
        }
        if (code == null || !payout.getConfirmationCode().equals(code.trim())) throw bad("Wrong code");
        long now = System.currentTimeMillis();
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(payoutId)),
                new Update().set("confirmed", true).set("confirmedAt", now).unset("confirmationCode"), DriverPayout.class);
        payout.setConfirmed(true);
        payout.setConfirmedAt(now);
        return payout;
    }

    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public List<DriverPayout> listPayouts(String driverId, String from, String to, Boolean unconfirmed, String officeId) {
        long[] range = range(from, to, 30);
        Criteria c = Criteria.where("companyId").is(CurrentUser.companyId()).and("paidAt").gte(range[0]).lt(range[1]);
        String office = officeOrNull(officeId);
        if (office != null) c = c.and("officeId").is(office);
        if (driverId != null && !driverId.isBlank()) c = c.and("driverId").is(driverId);
        if (Boolean.TRUE.equals(unconfirmed)) c = c.and("confirmed").is(false).and("reversed").is(false);
        return mongoTemplate.find(new Query(c).with(Sort.by(Sort.Direction.DESC, "paidAt")), DriverPayout.class);
    }

    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public DriverPayout getPayout(String payoutId) {
        return withBalanceAfter(requirePayout(payoutId));
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public DriverPayout reverse(String payoutId, String reason) {
        if (reason == null || reason.isBlank()) throw bad("A reason is required");
        User user = CurrentUser.get();
        DriverPayout payout = requirePayout(payoutId);
        DriverPayout claimed = mongoTemplate.findAndModify(
                new Query(Criteria.where("_id").is(payoutId).and("reversed").is(false)),
                new Update().set("reversed", true).set("reversedReason", reason.trim())
                        .set("reversedAt", System.currentTimeMillis()).set("reversedByName", user.getName()),
                FindAndModifyOptions.options().returnNew(true), DriverPayout.class);
        if (claimed == null) throw conflict("This payout was already reversed");
        unclaim(payoutId);
        for (DriverPayout.Adjustment a : payout.getAdjustments()) {
            if (a.getAdjustmentId() == null) continue;
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(a.getAdjustmentId())),
                    new Update().inc("repaidAmount", -a.getAmount()).set("open", true).unset("appliedPayoutId").unset("appliedAt"),
                    DriverAdjustment.class);
        }
        return claimed;
    }

    /** Old PUT driver-assignments/pay: one cash payout per driver, no adjustments. Returns lines paid. */
    public int legacyPay(List<DriverAssignment> lines, User user) {
        Map<String, List<DriverAssignment>> byDriver = new LinkedHashMap<>();
        for (DriverAssignment l : lines) {
            DriverFeeState st = DriverFeeLineService.stateOf(l);
            if (st == DriverFeeState.PAID || st == DriverFeeState.NOT_PAYABLE) continue;
            if (l.getDriverId() == null) {
                Driver d = feeLines.ensureDriver(l.getCompanyId(), l.getOfficeId(), l.getDriverName(), l.getDriverPhoneNumber(), null);
                if (d == null) continue;
                l.setDriverId(d.getId());
                mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(l.getId())),
                        new Update().set("driverId", d.getId()), DriverAssignment.class);
            }
            byDriver.computeIfAbsent(l.getDriverId() + "|" + l.getOfficeId(), k -> new ArrayList<>()).add(l);
        }
        int paid = 0;
        long now = System.currentTimeMillis();
        for (List<DriverAssignment> group : byDriver.values()) {
            DriverAssignment first = group.get(0);
            Driver driver = mongoTemplate.findById(first.getDriverId(), Driver.class);
            String payoutId = new ObjectId().toHexString();
            List<String> ids = group.stream().map(DriverAssignment::getId).toList();
            long claimed = mongoTemplate.updateMulti(new Query(Criteria.where("_id").in(ids).and("payed").is(false)
                            .orOperator(Criteria.where("state").in(DriverFeeState.WAITING, DriverFeeState.READY, DriverFeeState.ON_HOLD),
                                    Criteria.where("state").exists(false))),
                    new Update().set("state", DriverFeeState.PAID).set("payoutId", payoutId).set("paidAt", now)
                            .set("payed", true).set("whoPayedDriverName", user.getName())
                            .set("whoPayedDriverPhoneNumber", user.getPhoneNumber()),
                    DriverAssignment.class).getModifiedCount();
            if (claimed == 0) continue;
            List<DriverAssignment> paidLines = mongoTemplate.find(new Query(Criteria.where("payoutId").is(payoutId)), DriverAssignment.class);
            double gross = r(paidLines.stream().mapToDouble(DriverAssignment::getAmount).sum());
            mongoTemplate.insert(DriverPayout.builder()
                    .id(payoutId).receiptNo(nextReceiptNo(first.getOfficeId()))
                    .companyId(first.getCompanyId()).officeId(first.getOfficeId())
                    .driverId(first.getDriverId())
                    .driverName(driver != null ? driver.getName() : first.getDriverName())
                    .driverPhone(driver != null && !driver.getPhoneNumbers().isEmpty() ? driver.getPhoneNumbers().get(0) : first.getDriverPhoneNumber())
                    .lineIds(paidLines.stream().map(DriverAssignment::getId).collect(Collectors.toCollection(ArrayList::new)))
                    .tripCount((int) paidLines.stream().map(DriverAssignment::getTripId).distinct().count())
                    .grossAmount(gross).netAmount(gross)
                    .paymentMethod(CounterPaymentMethod.CASH)
                    .businessDate(counterService.openBusinessDate(first.getOfficeId()).toString())
                    .paidById(user.getUserId()).paidByName(user.getName()).paidAt(now)
                    .legacy(true)
                    .build());
            paid += paidLines.size();
        }
        return paid;
    }

    // ================================================================== 3.8 reports

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public DriverPaymentViews.PayoutReport payoutReport(String officeId, String from, String to) {
        String office = CurrentUser.scopedOfficeId(officeId);
        long[] range = range(from, to, 30);
        Criteria c = Criteria.where("companyId").is(CurrentUser.companyId()).and("reversed").is(false)
                .and("paidAt").gte(range[0]).lt(range[1]);
        if (office != null) c = c.and("officeId").is(office);
        List<DriverPayout> payouts = mongoTemplate.find(new Query(c), DriverPayout.class);
        Map<String, PayoutTotals> byStation = new LinkedHashMap<>();
        Map<String, PayoutTotals> byDriver = new LinkedHashMap<>();
        Map<String, String> officeNames = new HashMap<>();
        for (DriverPayout p : payouts) {
            String name = officeNames.computeIfAbsent(String.valueOf(p.getOfficeId()),
                    id -> officeRepository.findById(id).map(Office::getName).orElse(null));
            add(byStation.computeIfAbsent(p.getOfficeId(), k -> PayoutTotals.builder().officeId(p.getOfficeId()).officeName(name).build()), p);
            add(byDriver.computeIfAbsent(p.getDriverId() + "|" + p.getOfficeId(), k -> PayoutTotals.builder()
                    .officeId(p.getOfficeId()).officeName(name).driverId(p.getDriverId()).driverName(p.getDriverName()).build()), p);
        }
        Comparator<PayoutTotals> byNet = Comparator.comparingDouble(PayoutTotals::getNet).reversed();
        return DriverPaymentViews.PayoutReport.builder()
                .byStation(byStation.values().stream().sorted(byNet).toList())
                .byDriver(byDriver.values().stream().sorted(byNet).toList())
                .build();
    }

    private static void add(PayoutTotals t, DriverPayout p) {
        t.setPayouts(t.getPayouts() + 1);
        t.setGross(r(t.getGross() + p.getGrossAmount()));
        t.setDeductions(r(t.getDeductions() + p.getDeductions()));
        t.setBonuses(r(t.getBonuses() + p.getBonuses()));
        t.setNet(r(t.getNet() + p.getNetAmount()));
        if (p.getPaymentMethod() == CounterPaymentMethod.MOMO) t.setMomo(r(t.getMomo() + p.getNetAmount()));
        else t.setCash(r(t.getCash() + p.getNetAmount()));
        if (!p.isConfirmed()) t.setUnconfirmed(t.getUnconfirmed() + 1);
    }

    /** Trips where fewer parcels were received than the driver declared. */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public List<DriverPaymentViews.Trip> tripsWithMissingParcels(String officeId, String from, String to) {
        String office = CurrentUser.scopedOfficeId(officeId);
        long[] range = range(from, to, 30);
        Criteria c = Criteria.where("companyId").is(CurrentUser.companyId()).and("arrivedAt").gte(range[0]).lt(range[1])
                .and("declaredCount").ne(null);
        if (office != null) c = c.and("officeId").is(office);
        List<DriverTrip> trips = mongoTemplate.find(new Query(c).with(Sort.by(Sort.Direction.DESC, "arrivedAt")), DriverTrip.class)
                .stream().filter(t -> t.getReceivedCount() < t.getDeclaredCount()).toList();
        Map<String, List<DriverAssignment>> lines = linesForTrips(trips.stream().map(DriverTrip::getId).toList());
        return trips.stream().map(t -> tripView(t, lines.getOrDefault(t.getId(), List.of()), false)).toList();
    }

    /** Fees READY for more than the given number of days without being paid. */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public List<DriverAssignment> longWaitingFees(String officeId, int days) {
        String office = CurrentUser.scopedOfficeId(officeId);
        Criteria c = Criteria.where("companyId").is(CurrentUser.companyId()).and("state").is(DriverFeeState.READY)
                .and("readyAt").lt(System.currentTimeMillis() - Math.max(days, 0) * 86_400_000L);
        if (office != null) c = c.and("officeId").is(office);
        return mongoTemplate.find(new Query(c).with(Sort.by("readyAt")), DriverAssignment.class);
    }

    // ================================================================== migration

    /**
     * One-off: create driver records from existing fee lines' phone numbers, link the lines, and
     * give every line a state. Safe to run more than once.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public Map<String, Object> migrate() {
        int linked = 0, stated = 0, skipped = 0;
        Set<String> drivers = new java.util.HashSet<>();
        while (true) {
            List<DriverAssignment> batch = mongoTemplate.find(new Query(new Criteria().orOperator(
                    Criteria.where("state").exists(false),
                    Criteria.where("driverId").exists(false).and("migrationSkipped").ne(true))).limit(500), DriverAssignment.class);
            if (batch.isEmpty()) break;
            for (DriverAssignment l : batch) {
                Update u = new Update();
                if (l.getDriverId() == null) {
                    Driver d = feeLines.ensureDriver(l.getCompanyId(), l.getOfficeId(), l.getDriverName(), l.getDriverPhoneNumber(), null);
                    if (d != null) {
                        u.set("driverId", d.getId());
                        drivers.add(d.getId());
                        linked++;
                    } else {
                        u.set("migrationSkipped", true);
                        skipped++;
                    }
                }
                if (l.getState() == null) {
                    DriverFeeState st = initialState(l);
                    u.set("state", st);
                    if (st == DriverFeeState.READY) u.set("readyAt", System.currentTimeMillis()).set("readyReason", "MIGRATED");
                    stated++;
                }
                mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(l.getId())), u, DriverAssignment.class);
            }
        }
        return Map.of("linesLinked", linked, "linesGivenState", stated, "driversUsed", drivers.size(), "linesWithoutPhone", skipped);
    }

    private DriverFeeState initialState(DriverAssignment l) {
        if (l.isPayed()) return DriverFeeState.PAID;
        Parcel p = l.getParcelId() == null ? null : mongoTemplate.findById(l.getParcelId(), Parcel.class);
        if (p == null) return l.isDelivered() ? DriverFeeState.READY : DriverFeeState.WAITING;
        if (p.getParcelStatus() == shortly.mandmcorp.dev.shortly.enums.ParcelStatus.REVERSED) return DriverFeeState.NOT_PAYABLE;
        return p.isPickedUp() || p.isDelivered() || l.isDelivered() ? DriverFeeState.READY : DriverFeeState.WAITING;
    }

    // ================================================================== helpers

    /** Open balances per driver from the fee lines, plus net paid in the last 30 days. */
    private Map<String, Balances> balancesByDriver(String company, String office, String driverId) {
        Criteria c = Criteria.where("companyId").is(company).and("state").in(OPEN_STATES).and("driverId").ne(null);
        if (office != null) c = c.and("officeId").is(office);
        if (driverId != null) c = c.and("driverId").is(driverId);
        Map<String, Balances> out = new HashMap<>();
        for (Document d : mongoTemplate.aggregate(Aggregation.newAggregation(
                Aggregation.match(c),
                Aggregation.group("driverId", "state").sum("amount").as("amount").count().as("count")),
                DriverAssignment.class, Document.class).getMappedResults()) {
            Document id = (Document) d.get("_id");
            Balances b = out.computeIfAbsent(id.getString("driverId"), k -> new Balances());
            double amount = r(((Number) d.get("amount")).doubleValue());
            int count = ((Number) d.get("count")).intValue();
            switch (DriverFeeState.valueOf(id.getString("state"))) {
                case READY -> { b.setReadyAmount(amount); b.setReadyCount(count); }
                case WAITING -> { b.setWaitingAmount(amount); b.setWaitingCount(count); }
                case ON_HOLD -> { b.setOnHoldAmount(amount); b.setOnHoldCount(count); }
                default -> { }
            }
        }
        Criteria pc = Criteria.where("companyId").is(company).and("reversed").is(false)
                .and("paidAt").gte(System.currentTimeMillis() - 30 * 86_400_000L);
        if (office != null) pc = pc.and("officeId").is(office);
        if (driverId != null) pc = pc.and("driverId").is(driverId);
        for (Document d : mongoTemplate.aggregate(Aggregation.newAggregation(
                Aggregation.match(pc), Aggregation.group("driverId").sum("netAmount").as("net")),
                DriverPayout.class, Document.class).getMappedResults()) {
            out.computeIfAbsent(d.getString("_id"), k -> new Balances())
                    .setPaidLast30Days(r(((Number) d.get("net")).doubleValue()));
        }
        return out;
    }

    private Map<String, Long> lastTripByDriver(String company, String office) {
        Criteria c = Criteria.where("companyId").is(company);
        if (office != null) c = c.and("officeId").is(office);
        Map<String, Long> out = new HashMap<>();
        for (Document d : mongoTemplate.aggregate(Aggregation.newAggregation(
                Aggregation.match(c), Aggregation.group("driverId").max("arrivedAt").as("last")),
                DriverTrip.class, Document.class).getMappedResults()) {
            out.put(d.getString("_id"), ((Number) d.get("last")).longValue());
        }
        return out;
    }

    private Map<String, List<DriverAssignment>> linesForTrips(Collection<String> tripIds) {
        if (tripIds.isEmpty()) return Map.of();
        return mongoTemplate.find(new Query(Criteria.where("tripId").in(tripIds)), DriverAssignment.class)
                .stream().collect(Collectors.groupingBy(DriverAssignment::getTripId));
    }

    private DriverPaymentViews.Trip tripView(DriverTrip t, List<DriverAssignment> lines, boolean withLines) {
        boolean flagged = t.getDeclaredCount() != null && t.getDeclaredCount() != t.getReceivedCount();
        return DriverPaymentViews.Trip.builder()
                .tripId(t.getId()).vehicleNumber(t.getVehicleNumber()).origin(t.getOrigin())
                .arrivedAt(t.getArrivedAt()).receivedByName(t.getReceivedByName())
                .declaredCount(t.getDeclaredCount()).receivedCount(t.getReceivedCount()).flagged(flagged)
                .note(t.getNote()).driverId(t.getDriverId()).driverName(t.getDriverName())
                .feeTotal(r(lines.stream().filter(l -> l.getState() != DriverFeeState.NOT_PAYABLE).mapToDouble(DriverAssignment::getAmount).sum()))
                .readyAmount(r(lines.stream().filter(l -> l.getState() == DriverFeeState.READY).mapToDouble(DriverAssignment::getAmount).sum()))
                .fullyPaid(!lines.isEmpty() && lines.stream().allMatch(l -> l.getState() == DriverFeeState.PAID || l.getState() == DriverFeeState.NOT_PAYABLE))
                .lines(withLines ? lines.stream().map(this::lineView).toList() : List.of())
                .build();
    }

    private FeeLine lineView(DriverAssignment l) {
        return FeeLine.builder()
                .id(l.getId()).parcelId(l.getParcelId())
                .receiverName(l.getParcelInfo() != null ? l.getParcelInfo().getReceiverName() : null)
                .amount(l.getAmount()).state(DriverFeeLineService.stateOf(l))
                .readyAt(l.getReadyAt()).readyReason(l.getReadyReason()).holdReason(l.getHoldReason())
                .shelfName(l.getParcelInfo() != null ? l.getParcelInfo().getShelfName() : null)
                .payoutId(l.getPayoutId())
                .build();
    }

    private DriverPayout withBalanceAfter(DriverPayout p) {
        Balances b = balancesByDriver(p.getCompanyId(), p.getOfficeId(), p.getDriverId()).getOrDefault(p.getDriverId(), new Balances());
        p.setBalanceReadyAfter(b.getReadyAmount());
        p.setBalanceWaitingAfter(b.getWaitingAmount());
        return p;
    }

    private Driver requireDriver(String id) {
        Driver d = id == null ? null : mongoTemplate.findById(id, Driver.class);
        if (d == null || !Objects.equals(d.getCompanyId(), CurrentUser.companyId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found");
        }
        return d;
    }

    private DriverAssignment requireLine(String id) {
        DriverAssignment l = mongoTemplate.findById(id, DriverAssignment.class);
        User user = CurrentUser.get();
        if (l == null || !Objects.equals(l.getCompanyId(), user.getCompanyId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fee not found");
        }
        if (user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.SUPERADMIN
                && !Objects.equals(l.getOfficeId(), CurrentUser.officeId(user))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This fee belongs to another station");
        }
        return l;
    }

    private DriverPayout requirePayout(String id) {
        DriverPayout p = mongoTemplate.findById(id, DriverPayout.class);
        if (p == null || !Objects.equals(p.getCompanyId(), CurrentUser.companyId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payout not found");
        }
        return p;
    }

    /** Front desk and managers: their office. Admins: the requested office, else their own, else all. */
    private String officeOrNull(String requested) {
        User user = CurrentUser.get();
        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPERADMIN) {
            if (requested != null && !requested.isBlank()) return requested;
            return CurrentUser.officeId(user);
        }
        String own = CurrentUser.officeId(user);
        if (own == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have no assigned office");
        return own;
    }

    private void assertPhonesFree(List<String> phones, String exceptId) {
        Criteria c = Criteria.where("companyId").is(CurrentUser.companyId()).and("phoneNumbers").in(phones);
        if (exceptId != null) c = c.and("_id").ne(exceptId);
        Driver other = mongoTemplate.findOne(new Query(c), Driver.class);
        if (other != null) throw conflict("Phone number already belongs to driver " + other.getName());
    }

    private static List<String> normalizePhones(List<String> phones) {
        if (phones == null) return new ArrayList<>();
        return phones.stream().map(PhoneNumberUtils::normalizeGhana).filter(Objects::nonNull).distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static List<String> normalizeVehicles(List<String> vehicles) {
        if (vehicles == null) return new ArrayList<>();
        return vehicles.stream().filter(v -> v != null && !v.isBlank()).map(v -> v.trim().toUpperCase()).distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static boolean matches(Driver d, String term, String phoneTerm) {
        if (d.getName() != null && d.getName().toLowerCase().contains(term)) return true;
        if (d.getCompany() != null && d.getCompany().toLowerCase().contains(term)) return true;
        for (String p : d.getPhoneNumbers()) if (p.contains(term) || p.equals(phoneTerm)) return true;
        for (String v : d.getVehicles()) if (v.toLowerCase().contains(term)) return true;
        return false;
    }

    private static List<ObjectId> toObjectIds(Collection<String> ids) {
        return ids.stream().filter(ObjectId::isValid).map(ObjectId::new).toList();
    }

    /** from/to as epoch ms or yyyy-MM-dd (station days). Defaults to the last N days. */
    static long[] range(String from, String to, int defaultDays) {
        long end = to == null || to.isBlank() ? BusinessDay.endMs(BusinessDay.today()) : parseBound(to, true);
        long start = from == null || from.isBlank() ? end - defaultDays * 86_400_000L : parseBound(from, false);
        if (start >= end) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
        return new long[] { start, end };
    }

    private static long parseBound(String value, boolean end) {
        String v = value.trim();
        if (v.matches("\\d{10,}")) return Long.parseLong(v) + (end ? 1 : 0);
        LocalDate day = BusinessDay.parse(v);
        return end ? BusinessDay.endMs(day) : BusinessDay.startMs(day);
    }

    private static String mask(String phone) {
        return phone.length() <= 4 ? phone : "•••" + phone.substring(phone.length() - 4);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static double r(double v) {
        return CounterPickupRules.round2(v);
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }

    private static ResponseStatusException conflict(String m) {
        return new ResponseStatusException(HttpStatus.CONFLICT, m);
    }
}
