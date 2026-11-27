package shortly.mandmcorp.dev.shortly.service.driverpay;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.enums.DriverFeeState;
import shortly.mandmcorp.dev.shortly.enums.DriverPayPolicy;
import shortly.mandmcorp.dev.shortly.model.Driver;
import shortly.mandmcorp.dev.shortly.model.DriverAssignment;
import shortly.mandmcorp.dev.shortly.model.DriverTrip;
import shortly.mandmcorp.dev.shortly.model.Office;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.OfficeRepository;
import shortly.mandmcorp.dev.shortly.utils.BusinessDay;
import shortly.mandmcorp.dev.shortly.utils.PhoneNumberUtils;

/**
 * Lifecycle of driver fee lines (DriverAssignment): created WAITING (or READY under ON_ARRIVAL),
 * READY once the customer has paid, NOT_PAYABLE when the parcel is returned or cancelled.
 * Hooks are called from parcel intake, counter pickup, rider reconciliation and status changes.
 */
@Service
@Slf4j
public class DriverFeeLineService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final MongoTemplate mongoTemplate;
    private final OfficeRepository officeRepository;

    public DriverFeeLineService(MongoTemplate mongoTemplate, OfficeRepository officeRepository) {
        this.mongoTemplate = mongoTemplate;
        this.officeRepository = officeRepository;
    }

    public DriverPayPolicy policyFor(String officeId) {
        if (officeId == null) return DriverPayPolicy.ON_COLLECTION;
        return officeRepository.findById(officeId)
                .map(Office::getDriverPayPolicy)
                .orElse(null) == DriverPayPolicy.ON_ARRIVAL ? DriverPayPolicy.ON_ARRIVAL : DriverPayPolicy.ON_COLLECTION;
    }

    /** Finds a driver of the company by normalised phone, or creates one. Adds the office and vehicle. */
    public Driver ensureDriver(String companyId, String officeId, String name, String phone, String vehicle) {
        String normalized = PhoneNumberUtils.normalizeGhana(phone);
        if (normalized == null) return null;
        Query q = new Query(Criteria.where("companyId").is(companyId).and("phoneNumbers").is(normalized));
        Driver driver = mongoTemplate.findOne(q, Driver.class);
        if (driver == null) {
            driver = Driver.builder()
                    .companyId(companyId)
                    .name(name != null && !name.isBlank() ? name.trim() : normalized)
                    .phoneNumbers(new ArrayList<>(List.of(normalized)))
                    .momoNumber(normalized)
                    .build();
        }
        boolean changed = driver.getId() == null;
        if (officeId != null && !driver.getOfficeIds().contains(officeId)) {
            driver.getOfficeIds().add(officeId);
            changed = true;
        }
        if (vehicle != null && !vehicle.isBlank() && !driver.getVehicles().contains(vehicle.trim().toUpperCase())) {
            driver.getVehicles().add(vehicle.trim().toUpperCase());
            changed = true;
        }
        return changed ? mongoTemplate.save(driver) : driver;
    }

    /**
     * The trip a parcel belongs to: the given tripId, or the driver's open trip for the same
     * vehicle and station day, or a new one. Counts the parcel as received.
     */
    public DriverTrip attachToTrip(Driver driver, String tripId, String vehicle, String officeId, User receivedBy) {
        DriverTrip trip = null;
        if (tripId != null && !tripId.isBlank()) {
            trip = mongoTemplate.findById(tripId, DriverTrip.class);
            if (trip != null && !trip.getDriverId().equals(driver.getId())) trip = null;
        }
        String vehicleKey = vehicle == null || vehicle.isBlank() ? null : vehicle.trim().toUpperCase();
        String today = BusinessDay.today().toString();
        if (trip == null) {
            Query q = new Query(Criteria.where("driverId").is(driver.getId())
                    .and("businessDate").is(today).and("officeId").is(officeId).and("vehicleNumber").is(vehicleKey));
            trip = mongoTemplate.findOne(q, DriverTrip.class);
        }
        if (trip == null) {
            trip = mongoTemplate.save(DriverTrip.builder()
                    .companyId(driver.getCompanyId()).officeId(officeId)
                    .driverId(driver.getId()).driverName(driver.getName())
                    .vehicleNumber(vehicleKey).arrivedAt(System.currentTimeMillis()).businessDate(today)
                    .receivedById(receivedBy != null ? receivedBy.getUserId() : null)
                    .receivedByName(receivedBy != null ? receivedBy.getName() : null)
                    .build());
        }
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(trip.getId())),
                new Update().inc("receivedCount", 1), DriverTrip.class);
        trip.setReceivedCount(trip.getReceivedCount() + 1);
        return trip;
    }

    /** Sets driver/trip and the starting state on a new fee line (before it is saved). */
    public void initLine(DriverAssignment line, Driver driver, DriverTrip trip) {
        if (driver != null) line.setDriverId(driver.getId());
        if (trip != null) line.setTripId(trip.getId());
        if (policyFor(line.getOfficeId()) == DriverPayPolicy.ON_ARRIVAL) {
            line.setState(DriverFeeState.READY);
            line.setReadyAt(System.currentTimeMillis());
            line.setReadyReason("ARRIVED " + LocalTime.now(BusinessDay.ZONE).format(HH_MM));
        } else {
            line.setState(DriverFeeState.WAITING);
        }
        line.setPayed(false);
    }

    /** Customer paid at the counter: WAITING -> READY. */
    public void markPickedUp(String parcelId) {
        markReady(parcelId, "PICKED_UP");
    }

    /** Home delivery reconciled: WAITING -> READY. */
    public void markDelivered(String parcelId) {
        markReady(parcelId, "DELIVERED");
    }

    private void markReady(String parcelId, String event) {
        long now = System.currentTimeMillis();
        Query q = new Query(Criteria.where("parcelId").is(parcelId).orOperator(
                Criteria.where("state").is(DriverFeeState.WAITING),
                // legacy lines from before fee states
                Criteria.where("state").exists(false).and("payed").is(false)));
        long n = mongoTemplate.updateMulti(q, new Update()
                .set("state", DriverFeeState.READY)
                .set("readyAt", now)
                .set("readyReason", event + " " + LocalTime.now(BusinessDay.ZONE).format(HH_MM))
                .set("payed", false), DriverAssignment.class).getModifiedCount();
        if (n > 0) log.info("Driver fee for parcel {} is READY ({})", parcelId, event);
        // A held line keeps its hold but will return to READY on release.
        mongoTemplate.updateMulti(new Query(Criteria.where("parcelId").is(parcelId)
                        .and("state").is(DriverFeeState.ON_HOLD).and("stateBeforeHold").is(DriverFeeState.WAITING)),
                new Update().set("stateBeforeHold", DriverFeeState.READY).set("readyAt", now)
                        .set("readyReason", event + " " + LocalTime.now(BusinessDay.ZONE).format(HH_MM)),
                DriverAssignment.class);
    }

    /** Parcel returned to sender or cancelled: unpaid lines become NOT_PAYABLE. */
    public void markNotPayable(String parcelId) {
        Query q = new Query(Criteria.where("parcelId").is(parcelId)
                .and("state").nin(DriverFeeState.PAID, DriverFeeState.NOT_PAYABLE)
                .and("payed").is(false));
        mongoTemplate.updateMulti(q, new Update().set("state", DriverFeeState.NOT_PAYABLE), DriverAssignment.class);
    }

    /** Effective state of a line, including legacy lines saved before fee states existed. */
    public static DriverFeeState stateOf(DriverAssignment line) {
        if (line.getState() != null) return line.getState();
        if (line.isPayed()) return DriverFeeState.PAID;
        return line.isDelivered() ? DriverFeeState.READY : DriverFeeState.WAITING;
    }
}
