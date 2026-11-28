package shortly.mandmcorp.dev.shortly.service.rider.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.dto.request.DeliveryAssignmentRequest;
import shortly.mandmcorp.dev.shortly.dto.request.DeliveryStatusUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.FuelRequestDto;
import shortly.mandmcorp.dev.shortly.dto.request.FuelRequestUpdateDto;
import shortly.mandmcorp.dev.shortly.dto.request.ReconcilationRiderRequest;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryAssignmentResponse;
import shortly.mandmcorp.dev.shortly.dto.response.FuelRequestStatsResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;
import shortly.mandmcorp.dev.shortly.enums.FuelRequestStatus;
import shortly.mandmcorp.dev.shortly.enums.ParcelStatus;
import shortly.mandmcorp.dev.shortly.enums.UserRole;
import shortly.mandmcorp.dev.shortly.exceptions.EntityNotFound;
import shortly.mandmcorp.dev.shortly.exceptions.WrongCredentialsException;
import shortly.mandmcorp.dev.shortly.model.DeliveryAssignments;
import shortly.mandmcorp.dev.shortly.model.DriverAssignment;
import shortly.mandmcorp.dev.shortly.model.DriverReconcilation;
import shortly.mandmcorp.dev.shortly.model.FuelRequest;
import shortly.mandmcorp.dev.shortly.model.Office;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.ParcelInfo;
import shortly.mandmcorp.dev.shortly.model.Reconcilations;
import shortly.mandmcorp.dev.shortly.model.RiderInfo;
import shortly.mandmcorp.dev.shortly.model.RoutePlan;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.DeliveryAssignmentsRepository;
import shortly.mandmcorp.dev.shortly.repository.DriverAssignmentRepository;
import shortly.mandmcorp.dev.shortly.repository.DriverReconcilationRepository;
import shortly.mandmcorp.dev.shortly.repository.FuelRequestRepository;
import shortly.mandmcorp.dev.shortly.repository.OfficeRepository;
import shortly.mandmcorp.dev.shortly.repository.ParcelRepository;
import shortly.mandmcorp.dev.shortly.repository.ReconcilationRepository;
import shortly.mandmcorp.dev.shortly.repository.UserRepository;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationInterface;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationRequestTemplate;
import shortly.mandmcorp.dev.shortly.service.rider.RiderServiceInterface;
import shortly.mandmcorp.dev.shortly.utils.NotificationUtil;
import shortly.mandmcorp.dev.shortly.utils.OtpUtil;
import shortly.mandmcorp.dev.shortly.utils.CurrentUser;
import shortly.mandmcorp.dev.shortly.utils.GeoUtils;
import shortly.mandmcorp.dev.shortly.utils.ReconciliationValidator;
import shortly.mandmcorp.dev.shortly.utils.ParcelMapper;


/**
 * Service implementation for rider management operations.
 * Handles parcel assignments, delivery status updates, and rider queries.
 * 
 * @author Shortly Team
 * @version 1.0
 * @since 1.0
 */
@Service
@Slf4j
public class RiderServiceImplementation implements RiderServiceInterface {
    
    private final DeliveryAssignmentsRepository deliveryAssignmentsRepository;
    private final UserRepository userRepository;
    private final ParcelRepository parcelRepository;
    private final NotificationInterface notification;
    private final ParcelMapper parcelMapper;
    private MongoTemplate mongoTemplate;
    private final DeliveryAssignmentsRepository deliveryRepository;
    private final ReconcilationRepository reconcilationRepository;
    private final DriverReconcilationRepository driverReconcilationRepository;
    private final DriverAssignmentRepository driverAssignmentRepository;
    private final FuelRequestRepository fuelRequestRepository;
    private final OfficeRepository officeRepository;
    private final shortly.mandmcorp.dev.shortly.service.tracking.RiderTrackingService riderTrackingService;
    private final shortly.mandmcorp.dev.shortly.service.driverpay.DriverFeeLineService driverFeeLineService;
    private final shortly.mandmcorp.dev.shortly.service.driverpay.DriverPaymentService driverPaymentService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final shortly.mandmcorp.dev.shortly.service.common.IdempotencyService idempotencyService;
    private final shortly.mandmcorp.dev.shortly.service.customer.CustomerLinkService customerLinks;

    public RiderServiceImplementation(DeliveryAssignmentsRepository deliveryAssignmentsRepository, UserRepository userRepository, ParcelRepository parcelRepository,
        @Qualifier("smsNotification") NotificationInterface notification, ParcelMapper parcelMapper, MongoTemplate mongoTemplate,
        DeliveryAssignmentsRepository deliveryRepo, ReconcilationRepository reconcilationRepository, DriverReconcilationRepository drivRecon,
        DriverAssignmentRepository driverAssignmentRepository, FuelRequestRepository fuelRequestRepository, OfficeRepository officeRepository,
        shortly.mandmcorp.dev.shortly.service.tracking.RiderTrackingService riderTrackingService,
        shortly.mandmcorp.dev.shortly.service.driverpay.DriverFeeLineService driverFeeLineService,
        shortly.mandmcorp.dev.shortly.service.driverpay.DriverPaymentService driverPaymentService,
        org.springframework.transaction.support.TransactionTemplate transactionTemplate,
        shortly.mandmcorp.dev.shortly.service.common.IdempotencyService idempotencyService,
        shortly.mandmcorp.dev.shortly.service.customer.CustomerLinkService customerLinks) {
        this.deliveryAssignmentsRepository = deliveryAssignmentsRepository;
        this.userRepository = userRepository;
        this.parcelRepository = parcelRepository;
        this.notification = notification;
        this.parcelMapper = parcelMapper;
        this.mongoTemplate = mongoTemplate;
        this.deliveryRepository = deliveryRepo;
        this.reconcilationRepository = reconcilationRepository;
        this.driverReconcilationRepository = drivRecon;
        this.driverAssignmentRepository = driverAssignmentRepository;
        this.fuelRequestRepository = fuelRequestRepository;
        this.officeRepository = officeRepository;
        this.riderTrackingService = riderTrackingService;
        this.driverFeeLineService = driverFeeLineService;
        this.driverPaymentService = driverPaymentService;
        this.transactionTemplate = transactionTemplate;
        this.idempotencyService = idempotencyService;
        this.customerLinks = customerLinks;
    }

    /**
     * Generates a daily assignment ID in the format: {riderId}_{YYYYMMDD}
     * All assignments for the same rider on the same day will share this ID.
     *
     * @param riderId the rider's user ID
     * @param timestamp the assignment timestamp in milliseconds
     * @return formatted assignment ID
     */
    private String generateDailyAssignmentId(String riderId, long timestamp, String officeId) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String dateStr = dateFormat.format(new Date(timestamp));
        return riderId + "_" + dateStr + "_" + officeId;
    }

    /**
     * Generates a daily reconciliation ID in the format: {riderId}_{YYYYMMDD}
     * All reconciliations for the same rider on the same day will share this ID.
     *
     * @param riderId the rider's user ID
     * @param timestamp the reconciliation timestamp in milliseconds
     * @return formatted reconciliation ID
     */
    private String generateDailyReconciliationId(String riderId, long timestamp) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String dateStr = dateFormat.format(new Date(timestamp));
        return riderId + "_" + dateStr;
    }

    /**
     * Assigns multiple parcels to a rider.
     * Creates delivery assignments and sends SMS notification to rider.
     * Uses daily grouping - all parcels assigned to the same rider on the same day
     * are added to a single assignment with ID format: {riderId}_{YYYYMMDD}
     *
     * @param assignmentRequest contains rider ID and list of parcel IDs
     * @return UserResponse with success message
     * @throws EntityNotFound if rider or parcel not found
     */
    @Override
    @PreAuthorize("hasRole('FRONTDESK') or hasRole('ADMIN') or hasRole('MANAGER')")
    public UserResponse assignParcelsToRider(DeliveryAssignmentRequest assignmentRequest) {
        return assignParcelsToRider(assignmentRequest, null);
    }

    /**
     * Assigns parcels to a rider in one transaction. A parcel can be on only one active assignment:
     * the same rider again is a 409; another rider's active assignment loses the parcel ("Reassigned");
     * delivered, collected or returned-to-sender parcels are a 409. Each parcel is claimed with a
     * conditional update on activeAssignmentId, so two simultaneous requests can't both assign it.
     */
    @Override
    @PreAuthorize("hasRole('FRONTDESK') or hasRole('ADMIN') or hasRole('MANAGER')")
    public UserResponse assignParcelsToRider(DeliveryAssignmentRequest assignmentRequest, String idempotencyKey) {
        List<String> ids = assignmentRequest.getParcelIds();
        if (ids == null || ids.isEmpty()) throw badRequest("Parcel IDs list cannot be empty");
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) throw badRequest("Parcel IDs can't be blank");
            if (!seen.add(id)) throw badRequest("Parcel " + id + " is listed twice");
        }
        return idempotencyService.run("assign-parcels", idempotencyKey,
            () -> {
                List<NotificationRequestTemplate> sms = new ArrayList<>();
                UserResponse result;
                try {
                    result = transactionTemplate.execute(tx -> doAssign(assignmentRequest, sms));
                } catch (org.springframework.dao.DataAccessException e) {
                    // Write conflict: someone else changed one of these parcels at the same moment.
                    log.warn("Assignment conflict: {}", e.getMessage());
                    throw conflict("These parcels were just changed by someone else; reload and try again");
                }
                sms.forEach(notification::send);
                return result;
            },
            r -> List.of(r.getMessage()),
            rec -> new UserResponse(rec.getMessage() != null ? rec.getMessage() : "Parcels assigned successfully", null));
    }

    private UserResponse doAssign(DeliveryAssignmentRequest assignmentRequest, List<NotificationRequestTemplate> sms) {
        User frontDesk = CurrentUser.get();
        log.info("Assigning {} parcels to rider: {}", assignmentRequest.getParcelIds().size(), assignmentRequest.getRiderId());

        User rider = userRepository.findById(assignmentRequest.getRiderId())
            .orElseThrow(() -> new EntityNotFound("Rider not found"));
        if (rider.getRole() != UserRole.RIDER) throw badRequest("The chosen user is not a rider");

        String riderOfficeId = (rider.getOfficeIds() != null && !rider.getOfficeIds().isEmpty())
            ? rider.getOfficeIds().get(0)
            : null;
        if(riderOfficeId == null) {
            throw new EntityNotFound("Rider has no assigned office");
        }

        List<User> managers = userRepository.findByRoleAndOfficeIdsContaining(UserRole.MANAGER, riderOfficeId);
        User officeManager = !managers.isEmpty() ? managers.get(0) : rider;
        RiderInfo riderInfo = RiderInfo.builder()
                .riderId(rider.getUserId())
                .riderName(rider.getName())
                .riderPhoneNumber(rider.getPhoneNumber())
                .build();
        long assignedAt = System.currentTimeMillis();

        String officeId = CurrentUser.officeId(frontDesk);
        if (officeId == null) throw new WrongCredentialsException("User has no assigned office");
        String dailyAssignmentId = generateDailyAssignmentId(rider.getUserId(), assignedAt, officeId);

        // ---- 1. Check every parcel before changing anything (all or nothing).
        List<Parcel> toAssign = new ArrayList<>();
        Map<String, DeliveryAssignments> currentActive = new java.util.HashMap<>();
        for (String parcelId : assignmentRequest.getParcelIds()) {
            Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new EntityNotFound("Parcel not found: " + parcelId));
            if (!java.util.Objects.equals(parcel.getCompanyId(), frontDesk.getCompanyId())) {
                throw new EntityNotFound("Parcel not found: " + parcelId);
            }
            if (parcel.isDelivered()) throw conflict(parcelId + " is already delivered");
            if (parcel.getCounterPayment() != null
                    || (parcel.isPickedUp() && parcel.getTypeofParcel() != shortly.mandmcorp.dev.shortly.enums.ParcelTypes.PICKUP)) {
                throw conflict(parcelId + " was already collected at the counter");
            }
            if (parcel.getParcelStatus() == ParcelStatus.REVERSED) throw conflict(parcelId + " was returned to the sender");

            DeliveryAssignments active = findActiveAssignmentFor(parcelId);
            if (active != null && active.getRiderInfo() != null
                    && rider.getUserId().equals(active.getRiderInfo().getRiderId())) {
                throw conflict(rider.getName() + " already has " + parcelId);
            }

            if(!parcel.isHasCalled() || !parcel.isHomeDelivery()) {
                if(assignmentRequest.getParcelIds().size() == 1) {
                    throw new EntityNotFound("Parcel has not been called or is not for home delivery: " + parcelId);
                }
                log.warn("Parcel {} has not been called. Skipping assignment.", parcelId);
                continue;
            }
            toAssign.add(parcel);
            if (active != null) currentActive.put(parcelId, active);
        }

        // ---- 2. Write.
        DeliveryAssignments assignment = deliveryAssignmentsRepository.findById(dailyAssignmentId).orElse(null);
        if (assignment == null) {
            assignment = new DeliveryAssignments();
            assignment.setAssignmentId(dailyAssignmentId);
            assignment.setRiderInfo(riderInfo);
            assignment.setOfficeId(officeId);
            assignment.setCompanyId(frontDesk.getCompanyId());
            assignment.setStatus(DeliveryStatus.ASSIGNED);
            assignment.setConfirmationCode("");
            assignment.setAssignedAt(assignedAt);
            assignment.setCreatedAt(assignedAt);
            assignment.setParcels(new ArrayList<>());
            assignment.setAmount(0.0);
        } else if (assignment.getStatus() == DeliveryStatus.DELIVERED || assignment.getStatus() == DeliveryStatus.RETURNED
                || assignment.getStatus() == DeliveryStatus.CANCELLED || assignment.getStatus() == DeliveryStatus.COMPLETED) {
            // Today's assignment was finished; new parcels make it active again.
            assignment.setStatus(DeliveryStatus.ASSIGNED);
        }
        List<ParcelInfo> existingParcels = assignment.getParcels() != null ? assignment.getParcels() : new ArrayList<>();

        List<ParcelInfo> newParcels = new ArrayList<>();
        for (Parcel parcel : toAssign) {
            String parcelId = parcel.getParcelId();
            DeliveryAssignments old = currentActive.get(parcelId);
            if (old != null) {
                removeFromAssignment(old, parcelId, "Reassigned to " + rider.getName());
            }
            // Claim the parcel: only if nobody else claimed it since we read it.
            Criteria free = new Criteria().orOperator(
                Criteria.where("activeAssignmentId").exists(false),
                Criteria.where("activeAssignmentId").is(null),
                Criteria.where("activeAssignmentId").is(old != null ? old.getAssignmentId() : "__none__"));
            long claimed = mongoTemplate.updateFirst(
                new Query(new Criteria().andOperator(Criteria.where("_id").is(parcelId), free)),
                new Update().set("activeAssignmentId", dailyAssignmentId).set("parcelAssigned", true)
                    .set("riderId", rider.getUserId()).set("riderInfo", riderInfo),
                Parcel.class).getModifiedCount();
            if (claimed == 0) throw conflict(parcelId + " was just assigned by someone else; reload and try again");

            String confirmationCode = OtpUtil.generateOtp();
            ParcelInfo parcelInfo = toParcelInfo(parcel);
            newParcels.add(parcelInfo);
            assignment.setInboundCost(assignment.getInboundCost() + parcel.getInboundCost());
            assignment.setDeliveryCost(assignment.getDeliveryCost() + parcel.getDeliveryCost());

            String notifyReceiverSmsMessage = NotificationUtil.generateAssignmentMessgeCustomer(officeManager.getPhoneNumber(), rider.getName(), confirmationCode, parcel.getReceiverName(), parcel.getParcelId());
            sms.add(NotificationRequestTemplate.builder().body(customerLinks.appendLink(notifyReceiverSmsMessage, parcel))
                .to(parcel.getRecieverPhoneNumber()).otp(true).build());
        }

        if (newParcels.isEmpty()) {
            throw conflict("None of these parcels can be assigned (not called, or not for home delivery)");
        }
        existingParcels.addAll(newParcels);
        assignment.setParcels(existingParcels);
        RoutePlan plan = validRoutePlan(assignmentRequest, newParcels);
        if (plan != null) {
            assignment.setRoutePlan(plan);
        }
        assignment.setUpdatedAt(System.currentTimeMillis());
        assignment.setAmount(assignment.getDeliveryCost() + assignment.getInboundCost());
        deliveryAssignmentsRepository.save(assignment);
        log.info("Assigned {} parcels to rider: {}", newParcels.size(), rider.getName());
        sms.add(NotificationRequestTemplate.builder().body(NotificationUtil.genrateRiderAssMsg(rider.getName(), newParcels.size()))
            .to(rider.getPhoneNumber()).build());

        return new UserResponse("Parcels assigned successfully", rider.getPhoneNumber());
    }

    /** The parcel's active (ASSIGNED, ACCEPTED or PICKED_UP) assignment where it isn't finished, or null. */
    private DeliveryAssignments findActiveAssignmentFor(String parcelId) {
        List<DeliveryAssignments> found = mongoTemplate.find(new Query(Criteria.where("status")
                .in(DeliveryStatus.ASSIGNED, DeliveryStatus.ACCEPTED, DeliveryStatus.PICKED_UP)
                .and("parcels").elemMatch(Criteria.where("parcelId").is(parcelId)
                    .and("delivered").ne(true).and("returned").ne(true)))
            .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "assignedAt")),
            DeliveryAssignments.class);
        return found.isEmpty() ? null : found.get(0);
    }

    /** Takes a parcel off an assignment, adjusting totals; an assignment left empty is CANCELLED. */
    private void removeFromAssignment(DeliveryAssignments assignment, String parcelId, String reason) {
        ParcelInfo removed = assignment.getParcels().stream().filter(p -> parcelId.equals(p.getParcelId())).findFirst().orElse(null);
        if (removed == null) return;
        assignment.getParcels().remove(removed);
        assignment.setDeliveryCost(assignment.getDeliveryCost() - removed.getDeliveryCost());
        assignment.setInboundCost(assignment.getInboundCost() - removed.getInboundCost());
        assignment.setAmount(assignment.getDeliveryCost() + assignment.getInboundCost());
        if (assignment.getRoutePlan() != null && assignment.getRoutePlan().getStopOrder() != null) {
            assignment.getRoutePlan().getStopOrder().remove(parcelId);
        }
        if (assignment.getRemovedParcels() == null) assignment.setRemovedParcels(new ArrayList<>());
        assignment.getRemovedParcels().add(new DeliveryAssignments.RemovedParcel(parcelId, reason, System.currentTimeMillis()));
        boolean anyOpen = assignment.getParcels().stream().anyMatch(p -> !p.isDelivered() && !p.isReturned());
        if (assignment.getParcels().isEmpty()) {
            assignment.setStatus(DeliveryStatus.CANCELLED);
            assignment.setReturnReason(reason);
        } else if (!anyOpen && assignment.getParcels().stream().allMatch(ParcelInfo::isDelivered)) {
            assignment.setStatus(DeliveryStatus.DELIVERED);
        }
        assignment.setUpdatedAt(System.currentTimeMillis());
        deliveryAssignmentsRepository.save(assignment);
    }

    /** Clears the parcel's active-assignment claim (delivered, returned or removed). */
    private void releaseParcel(String parcelId) {
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(parcelId)),
            new Update().unset("activeAssignmentId"), Parcel.class);
    }

    private ParcelInfo toParcelInfo(Parcel parcel) {
        double prepaid = parcel.getDeliveryPrepaidAmount() != null ? parcel.getDeliveryPrepaidAmount() : 0;
        double parcelAmount = Math.max(0, parcel.getDeliveryCost() + parcel.getInboundCost() - prepaid);
        ParcelInfo info = ParcelInfo.builder()
            .parcelId(parcel.getParcelId())
            .receiverName(parcel.getReceiverName())
            .receiverPhoneNumber(parcel.getRecieverPhoneNumber())
            .receiverAddress(parcel.getReceiverAddress())
            .senderName(parcel.getSenderName())
            .parcelAmount(parcelAmount)
            .prepaidAmount(prepaid)
            .inboundCost(parcel.getInboundCost())
            .deliveryCost(parcel.getDeliveryCost())
            .senderPhoneNumber(parcel.getSenderPhoneNumber())
            .isPOD(parcel.isPOD())
            .isFragile(parcel.isFragile())
            .storageCost(parcel.getStorageCost())
            .pickUpCost(parcel.getPickUpCost())
            .parcelWeight(parcel.getParcelWeight())
            .pickedUp(parcel.isPickedUp())
            .homeDelivery(parcel.isHomeDelivery())
            .vehicleNumber(parcel.getVehicleNumber())
            .driverName(parcel.getDriverName())
            .driverPhoneNumber(parcel.getDriverPhoneNumber())
            .driverId(parcel.getDriverId())
            .officeId(parcel.getOfficeId())
            .paymentMethod(parcel.getPaymentMethod())
            .shelfName(parcel.getShelfName())
            .inboudPayed(parcel.isInboudPayed())
            .shelfId(parcel.getShelfId())
            .typeofParcel(parcel.getTypeofParcel())
            .ItemCost(parcel.getItemCost())
            .pickupAddress(parcel.getPickupAddress())
            .pickupContactName(parcel.getPickupContactName())
            .pickupContactPhoneNumber(parcel.getPickupContactPhoneNumber())
            .pickupInstructions(parcel.getPickupInstructions())
            .deliveryAddress(parcel.getDeliveryAddress())
            .deliveryContactName(parcel.getDeliveryContactName())
            .deliveryContactPhoneNumber(parcel.getDeliveryContactPhoneNumber())
            .specialInstructions(parcel.getSpecialInstructions())
            .imageUrls(parcel.getImageUrls())
            .build();
        ParcelMapper.copyLocation(parcel, info);
        return info;
    }

    /** Parcels that sit on more than one active assignment, with those assignments. */
    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public List<Map<String, Object>> findDuplicateAssignments() {
        String office = CurrentUser.scopedOfficeId(null);
        Criteria match = Criteria.where("companyId").is(CurrentUser.companyId())
            .and("status").in(DeliveryStatus.ASSIGNED, DeliveryStatus.ACCEPTED, DeliveryStatus.PICKED_UP);
        if (office != null) match = match.and("officeId").is(office);
        org.springframework.data.mongodb.core.aggregation.Aggregation agg = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
            org.springframework.data.mongodb.core.aggregation.Aggregation.match(match),
            org.springframework.data.mongodb.core.aggregation.Aggregation.unwind("parcels"),
            org.springframework.data.mongodb.core.aggregation.Aggregation.match(Criteria.where("parcels.delivered").ne(true).and("parcels.returned").ne(true)),
            org.springframework.data.mongodb.core.aggregation.Aggregation.group("parcels.parcelId").count().as("count")
                .push(new org.bson.Document("assignmentId", "$_id").append("riderName", "$riderInfo.riderName")
                    .append("riderId", "$riderInfo.riderId").append("assignedAt", "$assignedAt")).as("assignments"),
            org.springframework.data.mongodb.core.aggregation.Aggregation.match(Criteria.where("count").gt(1)));
        List<Map<String, Object>> out = new ArrayList<>();
        for (org.bson.Document d : mongoTemplate.aggregate(agg, DeliveryAssignments.class, org.bson.Document.class).getMappedResults()) {
            out.add(Map.of("parcelId", d.get("_id"), "assignments", d.get("assignments")));
        }
        return out;
    }

    /**
     * Keeps each duplicated parcel on its newest active assignment and takes it off the others,
     * then records activeAssignmentId on every parcel that is out with a rider. Safe to re-run.
     */
    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public Map<String, Object> fixDuplicateAssignments() {
        int removed = 0;
        for (Map<String, Object> dup : findDuplicateAssignments()) {
            String parcelId = (String) dup.get("parcelId");
            @SuppressWarnings("unchecked")
            List<org.bson.Document> list = new ArrayList<>((List<org.bson.Document>) dup.get("assignments"));
            list.sort((a, b) -> Long.compare(((Number) b.get("assignedAt")).longValue(), ((Number) a.get("assignedAt")).longValue()));
            String keep = list.get(0).getString("assignmentId");
            for (org.bson.Document other : list.subList(1, list.size())) {
                deliveryAssignmentsRepository.findById(other.getString("assignmentId"))
                    .ifPresent(a -> removeFromAssignment(a, parcelId, "Duplicate assignment removed"));
                removed++;
            }
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(parcelId)),
                new Update().set("activeAssignmentId", keep), Parcel.class);
        }
        int backfilled = 0;
        String office = CurrentUser.scopedOfficeId(null);
        Criteria c = Criteria.where("companyId").is(CurrentUser.companyId())
            .and("status").in(DeliveryStatus.ASSIGNED, DeliveryStatus.ACCEPTED, DeliveryStatus.PICKED_UP);
        if (office != null) c = c.and("officeId").is(office);
        for (DeliveryAssignments a : mongoTemplate.find(new Query(c), DeliveryAssignments.class)) {
            for (ParcelInfo p : a.getParcels()) {
                if (p.isDelivered() || p.isReturned()) continue;
                backfilled += (int) mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(p.getParcelId())
                        .orOperator(Criteria.where("activeAssignmentId").exists(false), Criteria.where("activeAssignmentId").is(null))),
                    new Update().set("activeAssignmentId", a.getAssignmentId()), Parcel.class).getModifiedCount();
            }
        }
        return Map.of("duplicatesRemoved", removed, "parcelsLinked", backfilled);
    }

    private static org.springframework.web.server.ResponseStatusException badRequest(String m) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, m);
    }

    private static org.springframework.web.server.ResponseStatusException conflict(String m) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, m);
    }

    /**
     * Returns the request's route plan if it passes validation, else null (the assignment still
     * goes ahead without it). Stops for parcels that were skipped during assignment are dropped.
     */
    private RoutePlan validRoutePlan(DeliveryAssignmentRequest request, List<ParcelInfo> assigned) {
        RoutePlan plan = request.getRoutePlan();
        if (plan == null) return null;
        if (!"GOOGLE".equals(plan.getMethod()) && !"ESTIMATE".equals(plan.getMethod())) {
            log.warn("Ignoring route plan: unknown method {}", plan.getMethod());
            return null;
        }
        if (plan.getStopOrder() == null || !request.getParcelIds().containsAll(plan.getStopOrder())) {
            log.warn("Ignoring route plan: stopOrder has ids not in parcelIds");
            return null;
        }
        if (plan.getEncodedPath() != null && plan.getEncodedPath().length() > RoutePlan.MAX_ENCODED_PATH_LENGTH) {
            log.warn("Ignoring route plan: encodedPath is {} chars", plan.getEncodedPath().length());
            return null;
        }
        java.util.Set<String> assignedIds = new java.util.HashSet<>();
        assigned.forEach(p -> assignedIds.add(p.getParcelId()));
        plan.setStopOrder(new ArrayList<>(plan.getStopOrder().stream().filter(assignedIds::contains).toList()));
        if (plan.getPlannedAt() == null) plan.setPlannedAt(System.currentTimeMillis());
        plan.setStale(false);
        return plan;
    }

    /**
     * Records when a parcel was marked done and where the rider's GPS was at that moment, plus the
     * distance to the drop-off pin. GPS failures never block the status update.
     */
    private void captureMarkedPosition(ParcelInfo info, String riderId) {
        info.setCompletedAt(System.currentTimeMillis());
        User rider = userRepository.findById(riderId).orElse(null);
        riderTrackingService.tryGetLocation(rider).ifPresent(location -> {
            info.setMarkedLatitude(location.getLat());
            info.setMarkedLongitude(location.getLng());
            info.setMarkedGpsTime(location.getGpsTime());
            if (info.getReceiverLatitude() != null && info.getReceiverLongitude() != null) {
                info.setMarkedDistanceFromDropoffM((double) Math.round(GeoUtils.haversineM(
                        location.getLat(), location.getLng(),
                        info.getReceiverLatitude(), info.getReceiverLongitude())));
            }
        });
    }

    /**
     * Retrieves assignments for authenticated rider.
     * 
     * @param onlyUndelivered if true, returns only non-delivered assignments
     * @return List of delivery assignments with full parcel details
     * @throws WrongCredentialsException if user not authenticated
     */
    @Override
    public List<DeliveryAssignments> getRiderAssignments(boolean onlyUndelivered) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        
        User rider = (User) auth.getPrincipal();
        List<DeliveryAssignments> assignments = onlyUndelivered
            ? deliveryAssignmentsRepository.findByRiderInfoRiderIdAndStatusNot(rider.getUserId(), DeliveryStatus.DELIVERED)
            : deliveryAssignmentsRepository.findByRiderInfoRiderId(rider.getUserId());

        return assignments;
    }

    /**
     * Updates delivery assignment status.
     * Auto-updates timestamps and parcel delivery flag.
     * 
     * @param assignmentId assignment to update
     * @param statusRequest new status
     * @return UserResponse with success message
     * @throws EntityNotFound if assignment not found
     * @throws WrongCredentialsException if not authorized
     */
    @Override
    public DeliveryAssignments updateDeliveryStatus(String assignmentId, DeliveryStatusUpdateRequest statusRequest) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        
        User rider = (User) auth.getPrincipal();
        DeliveryAssignments assignment = deliveryAssignmentsRepository.findById(assignmentId)
            .orElseThrow(() -> new EntityNotFound("Assignment not found"));
        //find the parcel using parcel
        Parcel parcelEntity = parcelRepository.findById(statusRequest.getParcelId()).orElseThrow(()-> new EntityNotFound("Parcel not found"));
        // Check if rider is authorized using embedded RiderInfo
        String assignedRiderId = assignment.getRiderInfo() != null ? assignment.getRiderInfo().getRiderId() : null;
        if(assignedRiderId == null || !assignedRiderId.equals(rider.getUserId())) {
            throw new WrongCredentialsException("Not authorized to update this assignment");
        }

        // Riders don't accept or decline: an assigned parcel goes straight to PICKED_UP, DELIVERED
        // or RETURNED. Only a manager can reassign or cancel.
        DeliveryStatus requested = statusRequest.getStatus();
        if (requested != DeliveryStatus.PICKED_UP && requested != DeliveryStatus.DELIVERED && requested != DeliveryStatus.RETURNED) {
            throw badRequest("Riders can only mark parcels picked up, delivered or returned");
        }
        if (requested == DeliveryStatus.PICKED_UP) {
            if (assignment.getStatus() == DeliveryStatus.ASSIGNED || assignment.getStatus() == DeliveryStatus.ACCEPTED) {
                assignment.setStatus(DeliveryStatus.PICKED_UP);
                assignment.setAcceptedAt(System.currentTimeMillis());
            }
        }

        if(statusRequest.getStatus() == DeliveryStatus.DELIVERED) {
           /* if( !statusRequest.getConfirmationCode().equals(assignment.getConfirmationCode())) {
                throw new ActionNotAllowed("Invalid  confirmation code");
            } */
           //check 
           if (parcelEntity.getInboundCost() > 0) {
            DriverAssignment driverAssignment = driverAssignmentRepository.findByParcelId(parcelEntity.getParcelId()).orElse(null);
            if(driverAssignment != null) {
                // The driver's fee is the parcel's inbound cost, set at intake. (This used to add the
                // inbound cost again on delivery, doubling the fee for home-delivered parcels.)

            driverAssignment.setDelivered(true);
            driverAssignmentRepository.save(driverAssignment);
            NotificationRequestTemplate notify = NotificationRequestTemplate.builder()
                .body(NotificationUtil.generateDriverDeliveredSms(driverAssignment.getDriverName(), parcelEntity.getParcelId(), driverAssignment.getAmount()))
                .to(parcelEntity.getDriverPhoneNumber())
                .build();
            notification.send(notify);
            }
           }

            // Fetch and update all parcels using parcelIds from embedded ParcelInfo list
            if(assignment.getParcels() != null && !assignment.getParcels().isEmpty()) {
                ParcelInfo selectedParcel = null;

                for(ParcelInfo parcelInfo : assignment.getParcels()) {
                    if(parcelInfo.getParcelId().equals(statusRequest.getParcelId())) {
                        selectedParcel = parcelInfo;
                        break;
                    }
                }
                    if(selectedParcel   !=  null) {
                        Parcel parcel = parcelRepository.findById(selectedParcel.getParcelId())
                            .orElseThrow(() -> new EntityNotFound("Parcel not found"));

                        parcel.setDelivered(true);
                        parcel.setPaymentMethod(statusRequest.getPayementMethod());
                        parcel.setParcelStatus(ParcelStatus.DELIVERD);
                        parcel.setActiveAssignmentId(null);
                        selectedParcel.setDelivered(true);
                        selectedParcel.setPaymentMethod(statusRequest.getPayementMethod());
                        captureMarkedPosition(selectedParcel, rider.getUserId());
                        releaseParcel(selectedParcel.getParcelId());

                        parcelRepository.save(parcel);

                        // Check if all parcels are delivered before marking assignment as DELIVERED
                        boolean allDelivered = assignment.getParcels().stream()
                            .allMatch(ParcelInfo::isDelivered);

                        if(allDelivered) {
                            assignment.setStatus(DeliveryStatus.DELIVERED);
                            assignment.setCompletedAt(System.currentTimeMillis());
                        }

                        String message = NotificationUtil.generateParcelStatusUpdateMsg(parcel.getParcelId(), "DELIVERED");
                        NotificationRequestTemplate notify = NotificationRequestTemplate.builder()
                            .body(message)
                            .to(parcel.getDriverPhoneNumber())
                            .build();
                        notification.send(notify);
                    } else {
                        throw new EntityNotFound("Parcel not found in assignment");
                    }
            }

        }
        else if(statusRequest.getStatus() == DeliveryStatus.RETURNED) {
            assignment.setReturnReason(statusRequest.getReturnReason());

            if(assignment.getParcels() != null && !assignment.getParcels().isEmpty() && statusRequest.getParcelId() != null) {
                ParcelInfo parcelToCancel = null;

                for(ParcelInfo parcelInfo : assignment.getParcels()) {
                    if(parcelInfo.getParcelId().equals(statusRequest.getParcelId())) {
                        parcelToCancel = parcelInfo;
                        break;
                    }
                }

                if(parcelToCancel != null && !parcelToCancel.isReturned()) {
                    Parcel parcel = parcelRepository.findById(parcelToCancel.getParcelId())
                        .orElseThrow(() -> new EntityNotFound("Parcel not found"));

                    parcel.setReturnCount(parcel.getReturnCount() + 1);
                    parcel.setDelivered(false);
                    parcel.setParcelAssigned(false);
                    parcel.setRiderId(null);
                    parcel.setActiveAssignmentId(null);
                    parcelRepository.save(parcel);

                    double parcelAmount = parcelToCancel.getParcelAmount();
                    assignment.setAmount(assignment.getAmount() - parcelAmount);
                    assignment.setDeliveryCost(assignment.getDeliveryCost() - parcelToCancel.getDeliveryCost());
                    assignment.setInboundCost(assignment.getInboundCost() - parcelToCancel.getInboundCost());

                    parcelToCancel.setReturned(true);
                    captureMarkedPosition(parcelToCancel, rider.getUserId());
                    releaseParcel(parcelToCancel.getParcelId());

                    boolean allReturned = assignment.getParcels().stream()
                        .allMatch(ParcelInfo::isReturned);

                    if(allReturned) {
                        assignment.setStatus(DeliveryStatus.RETURNED);
                    }
                }
            }
        }
        deliveryAssignmentsRepository.save(assignment);
        return assignment;
    }

    /**
     * Updates delivery assignment status.
     * Auto-updates timestamps and parcel delivery flag.
     * 
     * @param assignmentId assignment to update
     * @param statusRequest new status
     * @return UserResponse with success message
     * @throws EntityNotFound if assignment not found
     * @throws WrongCredentialsException if not authorized
     */
    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public DeliveryAssignments managerUpdateDeliveryStatus (String assignmentId, DeliveryStatusUpdateRequest statusRequest) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        
        User manager = (User) auth.getPrincipal();
        DeliveryAssignments assignment = deliveryAssignmentsRepository.findById(assignmentId)
            .orElseThrow(() -> new EntityNotFound("Assignment not found"));

        assignment.setStatus(statusRequest.getStatus());
        if(statusRequest.getStatus() == DeliveryStatus.DELIVERED) {
            assignment.setCompletedAt(System.currentTimeMillis());

            assignment.setCompletedAt(System.currentTimeMillis());
           /* if( !statusRequest.getConfirmationCode().equals(assignment.getConfirmationCode())) {
                throw new ActionNotAllowed("Invalid  confirmation code");
            } */

            if(assignment.getParcels() != null && !assignment.getParcels().isEmpty()) {
                ParcelInfo selectedParcel = null;

                for(ParcelInfo parcelInfo : assignment.getParcels()) {
                    if(parcelInfo.getParcelId().equals(statusRequest.getParcelId())) {
                        selectedParcel = parcelInfo;
                        break;
                    }
                }
                    if(selectedParcel   !=  null) {
                        Parcel parcel = parcelRepository.findById(selectedParcel.getParcelId())
                            .orElseThrow(() -> new EntityNotFound("Parcel not found"));

                        parcel.setDelivered(true);
                        parcel.setPaymentMethod(statusRequest.getPayementMethod());
                        parcel.setParcelStatus(shortly.mandmcorp.dev.shortly.enums.ParcelStatus.DELIVERD);
                        parcel.setActiveAssignmentId(null);
                        selectedParcel.setDelivered(true);
                        selectedParcel.setPaymentMethod(statusRequest.getPayementMethod());
                        selectedParcel.setCompletedAt(System.currentTimeMillis());
                        releaseParcel(selectedParcel.getParcelId());

                        parcelRepository.save(parcel);

                        String message = NotificationUtil.generateParcelStatusUpdateMsg(parcel.getParcelId(), "DELIVERED");
                        NotificationRequestTemplate notify = NotificationRequestTemplate.builder()
                            .body(message)
                            .to(parcel.getDriverPhoneNumber())
                            .build();
                        notification.send(notify);
                    } else {
                        throw new EntityNotFound("Parcel not found in assignment");
                    }
            } 
            
        }
        else if(statusRequest.getStatus() == DeliveryStatus.RETURNED) {
            assignment.setReturnReason(statusRequest.getReturnReason());

            if(assignment.getParcels() != null && !assignment.getParcels().isEmpty() && statusRequest.getParcelId() != null) {
                ParcelInfo parcelToCancel = null;

                for(ParcelInfo parcelInfo : assignment.getParcels()) {
                    if(parcelInfo.getParcelId().equals(statusRequest.getParcelId())) {
                        parcelToCancel = parcelInfo;
                        break;
                    }
                }

                if(parcelToCancel != null && !parcelToCancel.isReturned()) {
                    Parcel parcel = parcelRepository.findById(parcelToCancel.getParcelId())
                        .orElseThrow(() -> new EntityNotFound("Parcel not found"));

                    parcel.setReturnCount(parcel.getReturnCount() + 1);
                    parcel.setDelivered(false);
                    parcel.setParcelAssigned(false);
                    parcelRepository.save(parcel);

                    double parcelAmount = parcelToCancel.getParcelAmount();
                    assignment.setAmount(assignment.getAmount() - parcelAmount);

                    parcelToCancel.setReturned(true);
                    parcelToCancel.setCompletedAt(System.currentTimeMillis());
                    releaseParcel(parcelToCancel.getParcelId());

                    boolean allReturned = assignment.getParcels().stream()
                        .allMatch(ParcelInfo::isReturned);

                    if(allReturned) {
                        assignment.setStatus(DeliveryStatus.RETURNED);
                    }
                }
            }
        }
        deliveryAssignmentsRepository.save(assignment);
        return assignment;
    }


    /**
     * Searches rider's undelivered assignments by receiver phone number.
     * Provides quick search for order status updates.
     * 
     * @param receiverPhone receiver's phone number
     * @return List of matching undelivered assignments
     * @throws WrongCredentialsException if user not authenticated
     */
    @Override
    public List<DeliveryAssignments> searchByReceiverPhone(String receiverPhone) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        
        User rider = (User) auth.getPrincipal();
        return deliveryAssignmentsRepository.findByRiderAndReceiverPhoneAndNotDelivered(rider.getUserId(), receiverPhone);

    }

    /**
     * Converts DeliveryAssignments entity to response DTO.
     * Populates full parcel details using ParcelMapper.
     * 
     * @param assignment delivery assignment entity
     * @return DeliveryAssignmentResponse with populated data
     */
    private DeliveryAssignmentResponse toDeliveryAssignmentResponse(DeliveryAssignments assignment) {
        DeliveryAssignmentResponse response = new DeliveryAssignmentResponse();
        response.setAssignmentId(assignment.getAssignmentId());

        // Set rider information from embedded RiderInfo
        if (assignment.getRiderInfo() != null) {
            response.setRiderName(assignment.getRiderInfo().getRiderName());
            response.setRiderId(assignment.getRiderInfo().getRiderId());
        }

        // Fetch and set the full Parcel object if needed by the response (using first parcel)
        if (assignment.getParcels() != null && !assignment.getParcels().isEmpty()) {
            ParcelInfo firstParcel = assignment.getParcels().get(0);
            if (firstParcel.getParcelId() != null) {
                Parcel parcel = parcelRepository.findById(firstParcel.getParcelId()).orElse(null);
                response.setParcel(parcel);
            }
        }

        response.setStatus(assignment.getStatus());
        response.setAssignedAt(assignment.getAssignedAt());
        response.setAcceptedAt(assignment.getAcceptedAt());
        response.setCompletedAt(assignment.getCompletedAt());
        return response;
    }

    /**
     * Gets all delivery assignments for a specific rider with payment filter.
     * Returns assignments with full parcel details including driver, sender, and receiver.
     * 
     * @param riderId rider ID to get assignments for
     * @param payed filter by payment status
     * @return List of delivery assignments with full parcel details
     * @throws EntityNotFound if rider not found
     */
    @Override
    public List<DeliveryAssignments> getRiderAssignmentsByRiderId(String riderId, boolean payed) {
        return getRiderAssignmentsByRiderId(riderId, payed, null, null);
    }

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public List<DeliveryAssignments> getRiderAssignmentsByRiderId(String riderId, Boolean payed, Long from, Long to) {
        User rider = userRepository.findById(riderId)
            .orElseThrow(() -> new EntityNotFound("Rider not found"));
        User caller = CurrentUser.get();
        if (caller.getRole() != UserRole.ADMIN) {
            String own = CurrentUser.officeId(caller);
            if (own == null || rider.getOfficeIds() == null || !rider.getOfficeIds().contains(own)) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Rider is not in your office");
            }
        }

        if (from == null && to == null) {
            return payed == null
                ? deliveryAssignmentsRepository.findByRiderInfoRiderId(riderId)
                : deliveryAssignmentsRepository.findByRiderIdAndPayed(riderId, payed);
        }

        long start = from != null ? from : 0L;
        long end = to != null ? to : Long.MAX_VALUE;
        Query query = new Query(Criteria.where("riderInfo.riderId").is(riderId));
        if (payed != null) query.addCriteria(Criteria.where("payed").is(payed));
        query.addCriteria(new Criteria().orOperator(
            Criteria.where("assignedAt").gte(start).lte(end),
            Criteria.where("completedAt").gte(start).lte(end)));
        query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "assignedAt"));
        return mongoTemplate.find(query, DeliveryAssignments.class);
    }


    /**
     * Gets all assignments for parcels with specified status in an office with pagination.
     *
     * @param status status of the order to filter
     * @param pageable pagination parameters
     * @return Page of delivery assignments
     */
    @Override
    @PreAuthorize("hasRole('FRONTDESK') or hasRole('MANAGER') or hasRole('ADMIN')")
    public Page<DeliveryAssignments> getOrderAssignmentByStatus(DeliveryStatus status, Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        User frontDesk = (User) auth.getPrincipal();

        // Get the first office ID from user's office list
        String officeId = (frontDesk.getOfficeIds() != null && !frontDesk.getOfficeIds().isEmpty())
            ? frontDesk.getOfficeIds().get(0)
            : null;

        if(officeId == null) {
            throw new WrongCredentialsException("User has no assigned office");
        }

        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();

        criteria.add(Criteria.where("payed").is(false));
        criteria.add(Criteria.where("officeId").is(officeId));

        query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));

        org.springframework.data.domain.Sort sort;
        if (pageable.getSort().isSorted()) {
            sort = pageable.getSort();
        } else {
            sort = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "assignedAt");
        }
        query.with(sort);

        long total = mongoTemplate.count(query, DeliveryAssignments.class);

        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        List<DeliveryAssignments> assignments = mongoTemplate.find(query, DeliveryAssignments.class);
        return new PageImpl<>(assignments, pageable, total);
    }

    /**
     * Marks delivery assignment as paid for reconciliation.
     * Uses daily grouping - reconciliation ID format: {riderId}_{YYYYMMDD}
     * All reconciliations for the same rider on the same day are grouped together.
     *
     * @param reconcilationRiderRequest contains assignment ID and payment details
     * @return UserResponse with success message
     */
    @Override
    public UserResponse reconcilation(ReconcilationRiderRequest reconcilationRiderRequest) {
        log.info("Starting reconciliation for assignment: {}", reconcilationRiderRequest.getAssignmentId());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        User frontDesk = (User) auth.getPrincipal();

        Long reconciledAtTimestamp = reconcilationRiderRequest.getReconciledAt() != null
            ? reconcilationRiderRequest.getReconciledAt()
            : System.currentTimeMillis();

        DeliveryAssignments assignment = deliveryAssignmentsRepository.findById(reconcilationRiderRequest.getAssignmentId())
            .orElseThrow(() -> new EntityNotFound("Assignment not found"));
        ReconciliationValidator.validate(reconcilationRiderRequest, assignment);

        assignment.setPayed(true);
        assignment.setPayedAt(reconciledAtTimestamp);
        assignment.setPayedTo(frontDesk.getUserId());
        double payedAmount = reconcilationRiderRequest.getPayedAmount() != null ? reconcilationRiderRequest.getPayedAmount() : 0.0;
        assignment.setAmountPayed(assignment.getAmountPayed() + payedAmount);
        if (reconcilationRiderRequest.getExpectedAmount() != null) {
            assignment.setExpectedAmount(reconcilationRiderRequest.getExpectedAmount());
        }
        if (reconcilationRiderRequest.getVarianceAmount() != null) {
            assignment.setVarianceAmount(reconcilationRiderRequest.getVarianceAmount());
        }
        if (reconcilationRiderRequest.getLines() != null) {
            assignment.setReconciliationLines(reconcilationRiderRequest.getLines());
        }
        assignment.setReconciledAt(reconciledAtTimestamp);
        assignment.setReconciledById(frontDesk.getUserId());
        assignment.setReconciledByName(frontDesk.getName());
        assignment.setUpdatedAt(System.currentTimeMillis());
        deliveryAssignmentsRepository.save(assignment);
        // Customer money for delivered parcels is in: their drivers' fees become payable.
        if (assignment.getParcels() != null) {
            assignment.getParcels().stream().filter(ParcelInfo::isDelivered)
                .forEach(p -> driverFeeLineService.markDelivered(p.getParcelId()));
        }
        return new UserResponse("Reconciliation completed successfully", null);
    }

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public UserResponse reconcileBatch(List<ReconcilationRiderRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "No reconciliation items");
        }
        // Validate everything first so a bad item doesn't leave the batch half-applied.
        for (ReconcilationRiderRequest request : requests) {
            DeliveryAssignments assignment = deliveryAssignmentsRepository.findById(request.getAssignmentId())
                .orElseThrow(() -> new EntityNotFound("Assignment not found: " + request.getAssignmentId()));
            ReconciliationValidator.validate(request, assignment);
        }
        requests.forEach(this::reconcilation);
        return new UserResponse("Reconciliation completed successfully", null);
    }


    @Override
    public UserResponse resendConfirmationCodeToReceiver(String assignmentId) {
         DeliveryAssignments assignment = deliveryAssignmentsRepository.findById(assignmentId)
            .orElseThrow(() -> new EntityNotFound("Assignment not found"));

        String riderPhone = assignment.getRiderInfo() != null ? assignment.getRiderInfo().getRiderPhoneNumber() : "";
        String riderName = assignment.getRiderInfo() != null ? assignment.getRiderInfo().getRiderName() : "";

        // Get parcel info from first parcel in the list
        String receiverName = "";
        String parcelId = "";
        String receiverPhone = "";
        if (assignment.getParcels() != null && !assignment.getParcels().isEmpty()) {
            ParcelInfo firstParcel = assignment.getParcels().get(0);
            receiverName = firstParcel.getReceiverName() != null ? firstParcel.getReceiverName() : "";
            parcelId = firstParcel.getParcelId() != null ? firstParcel.getParcelId() : "";
            receiverPhone = firstParcel.getReceiverPhoneNumber() != null ? firstParcel.getReceiverPhoneNumber() : "";
        }

        String notifyReceiverSmsMessage = NotificationUtil.generateAssignmentMessgeCustomer(riderPhone, riderName, assignment.getConfirmationCode(), receiverName, parcelId);
        NotificationRequestTemplate notify = NotificationRequestTemplate.builder().body(notifyReceiverSmsMessage)
        .to(receiverPhone).otp(true).build();
        notification.send(notify);
        return UserResponse.builder().message("Successful sent").build();
    }

    @Override
    public Page<DeliveryAssignments> getAcitveAssignments(Pageable pageable, boolean payed) {
        /*Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        User frontDesk = (User) auth.getPrincipal();

        return this.deliveryRepository.findByPayedAndOfficeId(payed, frontDesk.getOfficeId());
        */
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        // Get the first office ID from user's office list
        String officeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
            ? user.getOfficeIds().get(0)
            : null;

        if (officeId == null) {
            throw new WrongCredentialsException("User has no office assigned");
        }


        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();

        criteria.add(Criteria.where("payed").is(payed));

        criteria.add(Criteria.where("officeId").is(officeId));

        query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));

        org.springframework.data.domain.Sort sort;
        if (pageable.getSort().isSorted()) {
            sort = pageable.getSort();
        } else {
            sort = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "assignedAt");
        }
        query.with(sort);

        // Count total documents matching criteria
        long total = mongoTemplate.count(query, DeliveryAssignments.class);

        // Apply pagination
        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        // Execute query
        List<DeliveryAssignments> assignments = mongoTemplate.find(query, DeliveryAssignments.class);

        return new PageImpl<>(assignments, pageable, total);
    }

    @Override
    public Page<DeliveryAssignments> getReturnedDeliveryAssignments(Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        // Get the first office ID from user's office list
        String officeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
            ? user.getOfficeIds().get(0)
            : null;

        if (officeId == null) {
            throw new WrongCredentialsException("User has no office assigned");
        }

        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();

        criteria.add(Criteria.where("status").is(DeliveryStatus.RETURNED));

        criteria.add(Criteria.where("officeId").is(officeId));

        query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));

        org.springframework.data.domain.Sort sort;
        if (pageable.getSort().isSorted()) {
            sort = pageable.getSort();
        } else {
            sort = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "assignedAt");
        }
        query.with(sort);

        // Count total documents matching criteria
        long total = mongoTemplate.count(query, DeliveryAssignments.class);

        // Apply pagination
        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        // Execute query
        List<DeliveryAssignments> assignments = mongoTemplate.find(query, DeliveryAssignments.class);

        return new PageImpl<>(assignments, pageable, total);
    }

    @Override
    public shortly.mandmcorp.dev.shortly.dto.response.ReconciliationStatsResponse getReconciliationStats(String period) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        // Get the first office ID from user's office list
        String officeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
            ? user.getOfficeIds().get(0)
            : null;

        if (officeId == null) {
            throw new WrongCredentialsException("User has no office assigned");
        }

        // Calculate time range based on period
        Long startTime = calculateStartTime(period);

        // Query for delivery assignments
        Query assignmentQuery = new Query();
        assignmentQuery.addCriteria(Criteria.where("officeId").is(officeId));
        assignmentQuery.addCriteria(Criteria.where("payed").is(true));

        if (startTime != null) {
            assignmentQuery.addCriteria(Criteria.where("assignedAt").gte(startTime));
        }

        List<DeliveryAssignments> completedAssignments =
            mongoTemplate.find(assignmentQuery, DeliveryAssignments.class);

        // Query for reconciliations
        Query reconciliationQuery = new Query();
        reconciliationQuery.addCriteria(Criteria.where("officeId").is(officeId));
        reconciliationQuery.addCriteria(Criteria.where("isCompleted").is(true));

        if (startTime != null) {
            reconciliationQuery.addCriteria(Criteria.where("reconciledAt").gte(startTime));
        }

        List<Reconcilations> reconciliations =
            mongoTemplate.find(reconciliationQuery, Reconcilations.class);

        // Calculate completed stats from reconciliations
        long completedCount = reconciliations.size();
        double completedAmount = reconciliations.stream()
            .mapToDouble(Reconcilations::getPayedAmount)
            .sum();

        // Query for not completed assignments (assigned but not yet reconciled/paid)
        Query notCompletedQuery = new Query();
        notCompletedQuery.addCriteria(Criteria.where("officeId").is(officeId));
        notCompletedQuery.addCriteria(Criteria.where("payed").is(false));
        notCompletedQuery.addCriteria(Criteria.where("status").in(
            DeliveryStatus.ASSIGNED,
            DeliveryStatus.ACCEPTED,
            DeliveryStatus.PICKED_UP,
            DeliveryStatus.DELIVERED
        ));

        if (startTime != null) {
            notCompletedQuery.addCriteria(Criteria.where("assignedAt").gte(startTime));
        }

        List<DeliveryAssignments> notCompletedAssignments =
            mongoTemplate.find(notCompletedQuery, DeliveryAssignments.class);

        long notCompletedCount = notCompletedAssignments.size();
        double notCompletedAmount = notCompletedAssignments.stream()
            .mapToDouble(DeliveryAssignments::getAmount)
            .sum();

        return shortly.mandmcorp.dev.shortly.dto.response.ReconciliationStatsResponse.builder()
            .completedCount(completedCount)
            .notCompletedCount(notCompletedCount)
            .completedAmount(completedAmount)
            .notCompletedAmount(notCompletedAmount)
            .totalAmount(completedAmount + notCompletedAmount)
            .totalCount(completedCount + notCompletedCount)
            .build();
    }

    private Long calculateStartTime(String period) {
        if (period == null || period.equalsIgnoreCase("all")) {
            return null;
        }

        long currentTime = System.currentTimeMillis();
        long millisecondsInDay = 24 * 60 * 60 * 1000L;

        return switch (period.toLowerCase()) {
            case "day" -> currentTime - millisecondsInDay;
            case "week" -> currentTime - (7 * millisecondsInDay);
            case "month" -> currentTime - (30 * millisecondsInDay);
            case "year" -> currentTime - (365 * millisecondsInDay);
            default -> currentTime - millisecondsInDay; // default to day
        };
    }

    @Override
    @PreAuthorize("hasRole('RIDER')")
    public Page<Reconcilations> getRiderReconciliations(Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        // Build query for rider's reconciliations
        Query query = new Query();
        query.addCriteria(Criteria.where("riderId").is(user.getUserId()));

        // Apply sorting from pageable, default to createdAt descending
        if (pageable.getSort().isSorted()) {
            query.with(pageable.getSort());
        } else {
            query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        }

        // Get total count
        long total = mongoTemplate.count(query, Reconcilations.class);

        // Apply pagination
        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        // Execute query
        List<Reconcilations> reconciliations = mongoTemplate.find(query, Reconcilations.class);
        return new PageImpl<>(reconciliations, pageable, total);
    }

    @Override
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public Page<Reconcilations> getOfficeReconciliations(Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        // Get the first office ID from user's office list
        String officeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
            ? user.getOfficeIds().get(0)
            : null;

        if (officeId == null) {
            throw new WrongCredentialsException("User has no office assigned");
        }

        // Build query for office reconciliations
        Query query = new Query();
        query.addCriteria(Criteria.where("officeId").is(officeId));

        // Apply sorting from pageable, default to createdAt descending
        if (pageable.getSort().isSorted()) {
            query.with(pageable.getSort());
        } else {
            query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        }

        // Get total count
        long total = mongoTemplate.count(query, Reconcilations.class);

        // Apply pagination
        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        // Execute query
        List<Reconcilations> reconciliations = mongoTemplate.find(query, Reconcilations.class);
        return new PageImpl<>(reconciliations, pageable, total);
    }

    @Override
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public Page<DeliveryAssignments> getReconciliationsByDate(Long date, String officeId, boolean useReconciledAt, Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        
        // Calculate start and end of the day for the given date
        long startOfDay = getStartOfDay(date);
        long endOfDay = getEndOfDay(date);

        log.info("Fetching reconciliations for office {} on date {} (start: {}, end: {}) using {} timestamp",
            officeId, date, startOfDay, endOfDay, useReconciledAt ? "reconciledAt" : "createdAt");
        // Build query for reconciliations by date
        Query query = new Query();
        query.addCriteria(Criteria.where("companyId").is(user.getCompanyId()));
        query.addCriteria(Criteria.where("officeId").is(officeId));
        query.addCriteria(Criteria.where("payed").is(true));

        // Filter by either reconciledAt or createdAt
    
        query.addCriteria(Criteria.where("createdAt").gte(startOfDay).lt(endOfDay));
        
        // Apply sorting from pageable, default to createdAt descending
        if (pageable.getSort().isSorted()) {
            query.with(pageable.getSort());
        } else {
            query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        }

        // Get total count
        long total = mongoTemplate.count(query, DeliveryAssignments.class);

        // Apply pagination
        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        // Execute query
        List<DeliveryAssignments> reconciliations = mongoTemplate.find(query, DeliveryAssignments.class);
        return new PageImpl<>(reconciliations, pageable, total);
    }

    /**
     * Gets the start of day (00:00:00.000) for a given timestamp
     */
    private long getStartOfDay(long timestamp) {
        long millisecondsInDay = 24 * 60 * 60 * 1000L;
        return (timestamp / millisecondsInDay) * millisecondsInDay;
    }

    /**
     * Gets the end of day (23:59:59.999) for a given timestamp
     */
    private long getEndOfDay(long timestamp) {
        return getStartOfDay(timestamp) + (24 * 60 * 60 * 1000L);
    }

    @Override
    @PreAuthorize("hasAnyRole('RIDER', 'MANAGER', 'ADMIN')")
    public UserResponse updateDeliveryAssignment(shortly.mandmcorp.dev.shortly.dto.request.DeliveryAssignmentUpdateRequest updateRequest) {
        log.info("Updating delivery assignment: {}", updateRequest.getAssignmentId());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        User user = (User) auth.getPrincipal();

        // Fetch the existing assignment
        DeliveryAssignments assignment = deliveryAssignmentsRepository.findById(updateRequest.getAssignmentId())
            .orElseThrow(() -> new EntityNotFound("Assignment not found"));

        // Authorization check
        if (user.getRole() == UserRole.RIDER) {
            String assignedRiderId = assignment.getRiderInfo() != null ? assignment.getRiderInfo().getRiderId() : null;
            if (assignedRiderId == null || !assignedRiderId.equals(user.getUserId())) {
                throw new WrongCredentialsException("Not authorized to update this assignment");
            }
        } else if (user.getRole() == UserRole.MANAGER || user.getRole() == UserRole.FRONTDESK) {
            if (!assignment.getOfficeId().equals(user.getOfficeId())) {
                throw new WrongCredentialsException("Not authorized to update assignments from other offices");
            }
        }

        boolean isRider = user.getRole() == UserRole.RIDER;

        // Update assignment fields if provided
        if (updateRequest.getRiderInfo() != null) {
            if (isRider) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Only a manager can reassign parcels");
            assignment.setRiderInfo(updateRequest.getRiderInfo());
        }

        if (updateRequest.getStatus() != null) {
            if (isRider && updateRequest.getStatus() != DeliveryStatus.PICKED_UP
                    && updateRequest.getStatus() != DeliveryStatus.DELIVERED && updateRequest.getStatus() != DeliveryStatus.RETURNED) {
                throw badRequest("Riders can only mark parcels picked up, delivered or returned");
            }
            assignment.setStatus(updateRequest.getStatus());
        }

        if (updateRequest.getReturnReason() != null) {
            assignment.setReturnReason(updateRequest.getReturnReason());
        }

        if (updateRequest.getPayementMethod() != null) {
            assignment.setPayementMethod(updateRequest.getPayementMethod());
        }

        if (!isRider) {
        assignment.setPayed(updateRequest.isPayed());

        if (updateRequest.getAmount() > 0) {
            assignment.setAmount(updateRequest.getAmount());
        }

        if (updateRequest.getInboundCost() > 0) {
            assignment.setInboundCost(updateRequest.getInboundCost());
        }

        if (updateRequest.getDeliveryCost() > 0) {
            assignment.setDeliveryCost(updateRequest.getDeliveryCost());
        }
        }

        // Update parcels if provided
        if (updateRequest.getParcels() != null && !updateRequest.getParcels().isEmpty()) {
            for (ParcelInfo updatedParcelInfo : updateRequest.getParcels()) {
                // Find the parcel in the assignment
                int parcelIndex = -1;
                ParcelInfo existingParcelInfo = null;

                for (int i = 0; i < assignment.getParcels().size(); i++) {
                    if (assignment.getParcels().get(i).getParcelId().equals(updatedParcelInfo.getParcelId())) {
                        existingParcelInfo = assignment.getParcels().get(i);
                        parcelIndex = i;
                        break;
                    }
                }

                if (existingParcelInfo != null) {
                    // Update the main Parcel database
                    Parcel parcel = parcelRepository.findById(updatedParcelInfo.getParcelId())
                        .orElseThrow(() -> new EntityNotFound("Parcel not found: " + updatedParcelInfo.getParcelId()));

                    // Sync changes to the main Parcel table
                    if (updatedParcelInfo.isDelivered() != existingParcelInfo.isDelivered()) {
                        parcel.setDelivered(updatedParcelInfo.isDelivered());
                        if (updatedParcelInfo.isDelivered()) {
                            parcel.setParcelStatus(shortly.mandmcorp.dev.shortly.enums.ParcelStatus.DELIVERD);
                        }
                    }

                    if (updatedParcelInfo.isReturned() != existingParcelInfo.isReturned()) {
                        if (updatedParcelInfo.isReturned() && !existingParcelInfo.isReturned()) {
                            parcel.setReturnCount(parcel.getReturnCount() + 1);
                            parcel.setParcelAssigned(false);
                        }
                    }

                    if (updatedParcelInfo.getPaymentMethod() != null) {
                        parcel.setPaymentMethod(updatedParcelInfo.getPaymentMethod());
                    }
                    if (updatedParcelInfo.isDelivered() || updatedParcelInfo.isReturned()) {
                        parcel.setActiveAssignmentId(null);
                    }

                    parcelRepository.save(parcel);

                    if (isRider) {
                        // Riders report status only; amounts and details stay as assigned.
                        existingParcelInfo.setDelivered(updatedParcelInfo.isDelivered());
                        existingParcelInfo.setReturned(updatedParcelInfo.isReturned());
                        if (updatedParcelInfo.getPaymentMethod() != null) existingParcelInfo.setPaymentMethod(updatedParcelInfo.getPaymentMethod());
                        if ((updatedParcelInfo.isDelivered() || updatedParcelInfo.isReturned()) && existingParcelInfo.getCompletedAt() == null) {
                            existingParcelInfo.setCompletedAt(System.currentTimeMillis());
                        }
                    } else {
                        assignment.getParcels().set(parcelIndex, updatedParcelInfo);
                    }
                }
            }
        }

        deliveryAssignmentsRepository.save(assignment);

        log.info("Successfully updated delivery assignment: {}", updateRequest.getAssignmentId());
        return new UserResponse("Delivery assignment updated successfully", user.getPhoneNumber());
    }


    @Override    
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public UserResponse removeParcelFromAssignment(String assignmentId, String parcelId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        User frontDesk = (User) auth.getPrincipal();
        
        DeliveryAssignments assignment = deliveryAssignmentsRepository.findById(assignmentId)
            .orElseThrow(() -> new EntityNotFound("Delivery assignment not found: " + assignmentId));

        String callerOffice = CurrentUser.officeId(frontDesk);
        if(frontDesk.getRole() != UserRole.ADMIN && (callerOffice == null || !callerOffice.equals(assignment.getOfficeId()))) {
            throw new WrongCredentialsException("User does not have permission to remove parcel from assignment");
        }
        ParcelInfo parcelInfoToRemove = null;
        for (ParcelInfo parcelInfo : assignment.getParcels()) {
            if (parcelInfo.getParcelId().equals(parcelId)) {
                parcelInfoToRemove = parcelInfo;
                break;
            }
        }
        if (parcelInfoToRemove == null) {
            throw new EntityNotFound("Parcel not found in assignment: " + parcelId);
        }
        Parcel parcel = parcelRepository.findById(parcelId)
            .orElseThrow(() -> new EntityNotFound("Parcel not found: " + parcelId));

        parcel.setParcelAssigned(false);
        parcel.setRiderInfo(null);
        parcelRepository.save(parcel);

        assignment.setDeliveryCost(assignment.getDeliveryCost() - parcelInfoToRemove.getDeliveryCost());
        assignment.setInboundCost(assignment.getInboundCost() - parcelInfoToRemove.getInboundCost());      
        
        assignment.getParcels().removeIf(parcelInfo -> parcelInfo.getParcelId().equals(parcelId));
        if (assignment.getRoutePlan() != null && assignment.getRoutePlan().getStopOrder() != null) {
            assignment.getRoutePlan().getStopOrder().remove(parcelId);
        }
        if (assignment.getRemovedParcels() == null) assignment.setRemovedParcels(new ArrayList<>());
        assignment.getRemovedParcels().add(new DeliveryAssignments.RemovedParcel(parcelId, "Removed by " + frontDesk.getName(), System.currentTimeMillis()));
        if (assignment.getParcels().isEmpty()) {
            assignment.setStatus(DeliveryStatus.CANCELLED);
        }
        releaseParcel(parcelId);


        deliveryAssignmentsRepository.save(assignment);

        log.info("Successfully removed parcel {} from delivery assignment {}", parcelId, assignmentId);
        return new UserResponse("Parcel removed from delivery assignment successfully", parcelId);
    }

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'ADMIN', 'MANAGER')")
    public Page<DriverReconcilation> getUnpaidDriverReconciliations(Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        User user = (User) auth.getPrincipal();
        String officeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
                ? user.getOfficeIds().get(0)
                : null;

        if (officeId == null) {
            throw new WrongCredentialsException("User has no assigned office");
        }

        return driverReconcilationRepository.findByOfficeIdAndCompanyIdAndPayedFalse(officeId, user.getCompanyId(), pageable);
    }

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'ADMIN', 'MANAGER')")
    public DriverReconcilation payDriverReconciliation(String reconciliationId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        DriverReconcilation reconciliation = driverReconcilationRepository.findByIdAndPayedFalse(reconciliationId)
                .orElseThrow(() -> new EntityNotFound("Driver reconciliation not found or already paid"));

        if (!java.util.Objects.equals(reconciliation.getCompanyId(), user.getCompanyId())) {
            throw new WrongCredentialsException("This reconciliation does not belong to your company");
        }

        reconciliation.setPayed(true);
        log.info("Driver reconciliation {} marked as paid", reconciliationId);
        return driverReconcilationRepository.save(reconciliation);
    }

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public Page<DriverAssignment> getUnpaidDriverAssignments(String driverPhoneNumber, Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        String officeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
                ? user.getOfficeIds().get(0) : null;

        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("officeId").is(officeId));
        criteriaList.add(Criteria.where("companyId").is(user.getCompanyId()));
        criteriaList.add(Criteria.where("payed").is(false));
        if (driverPhoneNumber != null && !driverPhoneNumber.isBlank()) {
            criteriaList.add(Criteria.where("driverPhoneNumber").is(driverPhoneNumber));
        }

        Query query = new Query(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.ASC, "driverPhoneNumber"));

        long total = mongoTemplate.count(query, DriverAssignment.class);
        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        return new PageImpl<>(mongoTemplate.find(query, DriverAssignment.class), pageable, total);
    }

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public UserResponse payDriverAssignments(List<String> assignmentIds) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        List<DriverAssignment> assignments = driverAssignmentRepository.findAllById(assignmentIds);

        if (assignments.isEmpty()) {
            throw new EntityNotFound("No driver assignments found for the provided IDs");
        }

        // Multi-tenant guard: every assignment must belong to the caller's company.
        for (DriverAssignment assignment : assignments) {
            if (!java.util.Objects.equals(assignment.getCompanyId(), user.getCompanyId())) {
                throw new WrongCredentialsException("One or more assignments do not belong to your company");
            }
        }

        // Old Driver Tracker: each driver gets a cash payout with no adjustments, so the payment
        // is recorded and shows in the counter cash-up.
        int paid = driverPaymentService.legacyPay(assignments, user);
        return new UserResponse("Driver assignments marked as paid successfully", String.valueOf(paid));
    }

    @Override
    @PreAuthorize("hasRole('RIDER')")
    public FuelRequest createFuelRequest(FuelRequestDto request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        String officeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
                ? user.getOfficeIds().get(0) : null;
        Office office = officeRepository.findById(officeId)
                .orElseThrow(() -> new EntityNotFound("Office not found for user"));
        RiderInfo riderInfo = RiderInfo.builder()
                .riderId(user.getUserId())
                .riderName(user.getName())
                .stationName(office.getName())
                .riderPhoneNumber(user.getPhoneNumber())
                .build();

        FuelRequest fuelRequest = new FuelRequest();
        fuelRequest.setRiderInfo(riderInfo);
        fuelRequest.setOfficeId(officeId);
        fuelRequest.setStation(request.getStation());
        fuelRequest.setFuleStationPhoneNumber(request.getFuelStationNumber());
        fuelRequest.setAttendantPhoneNumber(request.getAttendantNumber());
        fuelRequest.setNotes(request.getNotes());
        fuelRequest.setTank(request.getTank());
        String notifyFuelRequestMessage = NotificationUtil.generateFuelRequest(riderInfo.getRiderName(), office.getName());
        NotificationRequestTemplate notify = NotificationRequestTemplate.builder().body(notifyFuelRequestMessage)
                .to("+233504040228").build();
            notification.send(notify);
        return fuelRequestRepository.save(fuelRequest);
    }

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public Page<FuelRequest> getFuelRequests(Pageable pageable) {
        return fuelRequestRepository.findAll(pageable);
    }

    @Override
    @PreAuthorize("hasRole('RIDER')")
    public Page<FuelRequest> getRiderFuelRequests(Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        Pageable sorted = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(),
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        return fuelRequestRepository.findByRiderInfo_RiderId(user.getUserId(), sorted);
    }

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public FuelRequestStatsResponse getFuelRequestStats() {
        long total = fuelRequestRepository.count();
        long approved = fuelRequestRepository.countByStatus(FuelRequestStatus.APPROVED);
        long pending = fuelRequestRepository.countByStatus(FuelRequestStatus.PENDING);
        long rejected = fuelRequestRepository.countByStatus(FuelRequestStatus.REJECTED);
        return new FuelRequestStatsResponse(total, approved, pending, rejected);
    }

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public FuelRequest updateFuelRequest(String fuelRequestId, FuelRequestUpdateDto request) {
        FuelRequest fuelRequest = fuelRequestRepository.findById(fuelRequestId)
                .orElseThrow(() -> new EntityNotFound("Fuel request not found"));

        if (request.getStation() != null) fuelRequest.setStation(request.getStation());
        if (request.getFuelStationNumber() != null) fuelRequest.setFuleStationPhoneNumber(request.getFuelStationNumber());
        if (request.getAttendantNumber() != null) fuelRequest.setAttendantPhoneNumber(request.getAttendantNumber());
        if (request.getNotes() != null) fuelRequest.setNotes(request.getNotes());
        if (request.getAmount() != null) fuelRequest.setAmount(request.getAmount());
        if (request.getStatus() != null) fuelRequest.setStatus(request.getStatus());
        if (request.getTank() != null) fuelRequest.setTank(request.getTank());

        return fuelRequestRepository.save(fuelRequest);
    }

}