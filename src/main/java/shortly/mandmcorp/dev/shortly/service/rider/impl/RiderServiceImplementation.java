package shortly.mandmcorp.dev.shortly.service.rider.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.BulkOperations;
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
import shortly.mandmcorp.dev.shortly.dto.request.ReconcilationRiderRequest;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryAssignmentResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;
import shortly.mandmcorp.dev.shortly.enums.ReconcilationType;
import shortly.mandmcorp.dev.shortly.exceptions.EntityNotFound;
import shortly.mandmcorp.dev.shortly.exceptions.WrongCredentialsException;
import shortly.mandmcorp.dev.shortly.model.DeliveryAssignments;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.ParcelInfo;
import shortly.mandmcorp.dev.shortly.model.Reconcilations;
import shortly.mandmcorp.dev.shortly.model.RiderInfo;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.CancelationReasonRepository;
import shortly.mandmcorp.dev.shortly.repository.DeliveryAssignmentsRepository;
import shortly.mandmcorp.dev.shortly.repository.ParcelRepository;
import shortly.mandmcorp.dev.shortly.repository.ReconcilationRepository;
import shortly.mandmcorp.dev.shortly.repository.UserRepository;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationInterface;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationRequestTemplate;
import shortly.mandmcorp.dev.shortly.service.rider.RiderServiceInterface;
import shortly.mandmcorp.dev.shortly.utils.NotificationUtil;
import shortly.mandmcorp.dev.shortly.utils.OtpUtil;
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
    private final CancelationReasonRepository cancelationReasonRepo;
    private final DeliveryAssignmentsRepository deliveryRepository;
    private final ReconcilationRepository reconcilationRepository;

    public RiderServiceImplementation(DeliveryAssignmentsRepository deliveryAssignmentsRepository, UserRepository userRepository, ParcelRepository parcelRepository, 
        @Qualifier("smsNotification") NotificationInterface notification, ParcelMapper parcelMapper, MongoTemplate mongoTemplate, 
        CancelationReasonRepository cancelationReasonRepo, DeliveryAssignmentsRepository deliveryRepo, ReconcilationRepository reconcilationRepository  ) {
        this.deliveryAssignmentsRepository = deliveryAssignmentsRepository;
        this.userRepository = userRepository;
        this.parcelRepository = parcelRepository;
        this.notification = notification;
        this.parcelMapper = parcelMapper;
        this.mongoTemplate = mongoTemplate;
        this.cancelationReasonRepo = cancelationReasonRepo;
        this.deliveryRepository = deliveryRepo;
        this.reconcilationRepository = reconcilationRepository;
    }
    
    /**
     * Assigns multiple parcels to a rider.
     * Creates delivery assignments and sends SMS notification to rider.
     * 
     * @param assignmentRequest contains rider ID and list of parcel IDs
     * @return UserResponse with success message
     * @throws EntityNotFound if rider or parcel not found
     */
    @Override
    @PreAuthorize("hasRole('FRONTDESK') or hasRole('ADMIN') or hasRole('MANAGER')")
    public UserResponse assignParcelsToRider(DeliveryAssignmentRequest assignmentRequest) {
        log.info("Assigning {} parcels to rider: {}", assignmentRequest.getParcelIds().size(), assignmentRequest.getRiderId());
        
        User rider = userRepository.findById(assignmentRequest.getRiderId())
            .orElseThrow(() -> new EntityNotFound("Rider not found"));
        
        long assignedAt = System.currentTimeMillis();
        String confirmationCode = "";
        for(String parcelId : assignmentRequest.getParcelIds()) {
            Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new EntityNotFound("Parcel not found: " + parcelId));
            if(!parcel.isHasCalled() ||!parcel.isHomeDelivery()) {
                log.warn("Parcel {} has not been called. Skipping assignment.", parcelId);
                continue;
            }
            confirmationCode = OtpUtil.generateOtp();

            // Create embedded RiderInfo
            RiderInfo riderInfo = RiderInfo.builder()
                .riderId(rider.getUserId())
                .riderName(rider.getName())
                .riderPhoneNumber(rider.getPhoneNumber())
                .build();

            // Create embedded ParcelInfo
            ParcelInfo parcelInfo = ParcelInfo.builder()
                .parcelId(parcel.getParcelId())
                .parcelDescription(parcel.getParcelDescription())
                .receiverName(parcel.getReceiverName())
                .receiverPhoneNumber(parcel.getRecieverPhoneNumber())
                .receiverAddress(parcel.getReceiverAddress())
                .senderName(parcel.getSenderName())
                .senderPhoneNumber(parcel.getSenderPhoneNumber())
                .build();

            DeliveryAssignments assignment = new DeliveryAssignments();
            assignment.setRiderInfo(riderInfo);
            assignment.setOfficeId(rider.getOfficeId());
            assignment.setParcelInfo(parcelInfo);
            assignment.setStatus(DeliveryStatus.ASSIGNED);
            assignment.setConfirmationCode(confirmationCode);
            assignment.setAssignedAt(assignedAt);
            parcel.setParcelAssigned(true);
            deliveryAssignmentsRepository.save(assignment);
            parcelRepository.save(parcel);

            Reconcilations reconcilation = new Reconcilations();
            reconcilation.setAssignmentId(assignment.getAssignmentId());
            double amount = parcel.getDeliveryCost() + parcel.getInboundCost();
            reconcilation.setAmount(amount);
            reconcilation.setOfficeId(rider.getOfficeId());
            reconcilation.setRiderName(rider.getName());
            reconcilation.setRiderId(rider.getUserId());
            reconcilation.setRiderPhoneNumber(rider.getPhoneNumber());
            reconcilation.setParcelId(parcel.getParcelId());
            reconcilation.setCreatedAt(System.currentTimeMillis());
            reconcilationRepository.save(reconcilation);

        String notifyReceiverSmsMessage = NotificationUtil.generateAssignmentMessgeCustomer(rider.getPhoneNumber(), rider.getName(), confirmationCode, parcel.getReceiverName(), parcel.getParcelId());
        NotificationRequestTemplate notify = NotificationRequestTemplate.builder().body(notifyReceiverSmsMessage)
        .to(parcel.getRecieverPhoneNumber()).build();
        notification.send(notify);
            
        }
        
        log.info("Successfully assigned {} parcels to rider: {}", assignmentRequest.getParcelIds().size(), rider.getName());
        NotificationRequestTemplate notify = NotificationRequestTemplate.builder().body(NotificationUtil.genrateRiderAssMsg(rider.getName(), assignmentRequest.getParcelIds().size()))
        .to(rider.getPhoneNumber()).build();
        notification.send(notify);
        return new UserResponse("Parcels assigned successfully", rider.getPhoneNumber());
    }

    /**
     * Retrieves assignments for authenticated rider.
     * 
     * @param onlyUndelivered if true, returns only non-delivered assignments
     * @return List of delivery assignments with full parcel details
     * @throws WrongCredentialsException if user not authenticated
     */
    @Override
    public List<DeliveryAssignmentResponse> getRiderAssignments(boolean onlyUndelivered) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        
        User rider = (User) auth.getPrincipal();
        List<DeliveryAssignments> assignments = onlyUndelivered
            ? deliveryAssignmentsRepository.findByRiderInfoRiderIdAndStatusNot(rider.getUserId(), DeliveryStatus.DELIVERED)
            : deliveryAssignmentsRepository.findByRiderInfoRiderId(rider.getUserId());

        return assignments.stream().map(this::toDeliveryAssignmentResponse).collect(Collectors.toList());
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
    public UserResponse updateDeliveryStatus(String assignmentId, DeliveryStatusUpdateRequest statusRequest) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        
        User rider = (User) auth.getPrincipal();
        DeliveryAssignments assignment = deliveryAssignmentsRepository.findById(assignmentId)
            .orElseThrow(() -> new EntityNotFound("Assignment not found"));

        // Check if rider is authorized using embedded RiderInfo
        String assignedRiderId = assignment.getRiderInfo() != null ? assignment.getRiderInfo().getRiderId() : null;
        if(assignedRiderId == null || !assignedRiderId.equals(rider.getUserId())) {
            throw new WrongCredentialsException("Not authorized to update this assignment");
        }

        assignment.setStatus(statusRequest.getStatus());
        if(statusRequest.getStatus() == DeliveryStatus.DELIVERED) {
            assignment.setCompletedAt(System.currentTimeMillis());
           /* if( !statusRequest.getConfirmationCode().equals(assignment.getConfirmationCode())) {
                throw new ActionNotAllowed("Invalid  confirmation code");
            } */

            // Fetch and update the parcel using parcelId from embedded ParcelInfo
            String parcelId = assignment.getParcelInfo() != null ? assignment.getParcelInfo().getParcelId() : null;
            if(parcelId != null) {
                Parcel parcel = parcelRepository.findById(parcelId)
                    .orElseThrow(() -> new EntityNotFound("Parcel not found"));

                parcel.setDelivered(true);
                parcelRepository.save(parcel);

                String message = NotificationUtil.generateParcelStatusUpdateMsg(parcel.getParcelId(), "DELIVERED");
                NotificationRequestTemplate notify = NotificationRequestTemplate.builder()
                    .body(message)
                    .to(parcel.getDriverPhoneNumber())
                    .build();
                notification.send(notify);
            }

            assignment.setStatus(DeliveryStatus.DELIVERED);
            assignment.setPayed(true);
            assignment.setPayementMethod(statusRequest.getPayementMethod());
        }
        else if(statusRequest.getStatus() == DeliveryStatus.CANCELLED) {
            // Fetch and update the parcel using parcelId from embedded ParcelInfo
            String parcelId = assignment.getParcelInfo() != null ? assignment.getParcelInfo().getParcelId() : null;
            if(parcelId != null) {
                Parcel parcel = parcelRepository.findById(parcelId)
                    .orElseThrow(() -> new EntityNotFound("Parcel not found"));

                assignment.setCancelationReason(statusRequest.getCancelationReason());
                assignment.setStatus(DeliveryStatus.CANCELLED);
                parcel.setCancelationCount(parcel.getCancelationCount() + 1);
                parcel.setDelivered(false);
                parcel.setParcelAssigned(false);
                parcelRepository.save(parcel);
            }
        }
        deliveryAssignmentsRepository.save(assignment);
        return new UserResponse("Delivery status updated successfully", rider.getPhoneNumber());
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
    //manager or admin
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public UserResponse managerUpdateDeliveryStatus (String assignmentId, DeliveryStatusUpdateRequest statusRequest) {
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

            // Fetch and update the parcel using parcelId from embedded ParcelInfo
            String parcelId = assignment.getParcelInfo() != null ? assignment.getParcelInfo().getParcelId() : null;
            if(parcelId != null) {
                Parcel parcel = parcelRepository.findById(parcelId)
                    .orElseThrow(() -> new EntityNotFound("Parcel not found"));

                parcel.setDelivered(true);
                parcelRepository.save(parcel);

                String message = NotificationUtil.generateParcelStatusUpdateMsg(parcel.getParcelId(), "DELIVERED");
                NotificationRequestTemplate notify = NotificationRequestTemplate.builder()
                    .body(message)
                    .to(parcel.getDriverPhoneNumber())
                    .build();
                notification.send(notify);
            }

            assignment.setStatus(DeliveryStatus.DELIVERED);
            assignment.setPayed(true);
        }
        else if(statusRequest.getStatus() == DeliveryStatus.CANCELLED) {
            // Fetch and update the parcel using parcelId from embedded ParcelInfo
            String parcelId = assignment.getParcelInfo() != null ? assignment.getParcelInfo().getParcelId() : null;
            if(parcelId != null) {
                Parcel parcel = parcelRepository.findById(parcelId)
                    .orElseThrow(() -> new EntityNotFound("Parcel not found"));

                assignment.setCancelationReason(statusRequest.getCancelationReason());
                parcel.setDelivered(false);
                parcel.setParcelAssigned(false);
                parcelRepository.save(parcel);
            }
        }
        deliveryAssignmentsRepository.save(assignment);

        // Get rider phone from embedded RiderInfo
        String riderPhone = assignment.getRiderInfo() != null ? assignment.getRiderInfo().getRiderPhoneNumber() : "";
        return new UserResponse("Delivery status updated successfully", riderPhone);
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
    public List<DeliveryAssignmentResponse> searchByReceiverPhone(String receiverPhone) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        
        User rider = (User) auth.getPrincipal();
        List<DeliveryAssignments> assignments = deliveryAssignmentsRepository.findByRiderAndReceiverPhoneAndNotDelivered(rider.getUserId(), receiverPhone);

        return assignments.stream().map(this::toDeliveryAssignmentResponse).collect(Collectors.toList());
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

        // Fetch and set the full Parcel object if needed by the response
        if (assignment.getParcelInfo() != null && assignment.getParcelInfo().getParcelId() != null) {
            Parcel parcel = parcelRepository.findById(assignment.getParcelInfo().getParcelId()).orElse(null);
            response.setParcel(parcel);
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
        if(!userRepository.existsById(riderId)) {
            throw new EntityNotFound("Rider not found");
        }

        return deliveryAssignmentsRepository.findByRiderIdAndPayed(riderId, payed);
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

        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();

        criteria.add(Criteria.where("status").is(status));
        criteria.add(Criteria.where("officeId").is(frontDesk.getOfficeId()));

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
     * Marks multiple delivery assignments as paid for reconciliation.
     * Uses MongoDB bulk operations for efficient batch updates.
     * Non-existent assignment IDs are silently skipped without errors.
     * 
     * @param reconcilationRiderRequest contains rider ID and list of assignment IDs
     * @return UserResponse with success message
     */
    @Override
    public UserResponse reconcilation(ReconcilationRiderRequest reconcilationRiderRequest) {
        log.info("Starting reconciliation for rider");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        User frontDesk = (User) auth.getPrincipal();

        Long reconciledAtTimestamp = reconcilationRiderRequest.getReconciledAt() != null
            ? reconcilationRiderRequest.getReconciledAt()
            : System.currentTimeMillis();

    BulkOperations bulk =
        mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, DeliveryAssignments.class);

    for (String id : reconcilationRiderRequest.getAssignmentIds()) {

    Update update = new Update()
            .set("status", DeliveryStatus.COMPLETED.name())
            .set("payed", true);
    bulk.updateOne(
            Query.query(Criteria.where("assignmentId").is(id)),
            update
    );

    Reconcilations reconcilation = reconcilationRepository.findByAssignmentId(id)
            .orElse(new Reconcilations());

    DeliveryAssignments assignment = deliveryAssignmentsRepository.findById(id).orElse(null);

    if (assignment != null) {
        reconcilation.setAssignmentId(id);
        reconcilation.setPayedTo(frontDesk.getUserId());
        reconcilation.setType(ReconcilationType.RIDER);
        reconcilation.setParcelId(assignment.getParcelInfo() != null ? assignment.getParcelInfo().getParcelId() : null);
        reconcilation.setRiderId(assignment.getRiderInfo() != null ? assignment.getRiderInfo().getRiderId() : null);
        reconcilation.setRiderName(assignment.getRiderInfo() != null ? assignment.getRiderInfo().getRiderName() : null);
        reconcilation.setRiderPhoneNumber(assignment.getRiderInfo() != null ? assignment.getRiderInfo().getRiderPhoneNumber() : null);
        reconcilation.setOfficeId(assignment.getOfficeId());
        reconcilation.setCompleted(true);
        reconcilation.setReconciledAt(reconciledAtTimestamp);

        reconcilationRepository.save(reconcilation);
    }
    }
    bulk.execute();
    return new UserResponse("Reconciliation completed successfully", null);
    }


    @Override
    public UserResponse resendConfirmationCodeToReceiver(String assignmentId) {
         DeliveryAssignments assignment = deliveryAssignmentsRepository.findById(assignmentId)
            .orElseThrow(() -> new EntityNotFound("Assignment not found"));

        String riderPhone = assignment.getRiderInfo() != null ? assignment.getRiderInfo().getRiderPhoneNumber() : "";
        String riderName = assignment.getRiderInfo() != null ? assignment.getRiderInfo().getRiderName() : "";
        String receiverName = assignment.getParcelInfo() != null ? assignment.getParcelInfo().getReceiverName() : "";
        String parcelId = assignment.getParcelInfo() != null ? assignment.getParcelInfo().getParcelId() : "";
        String receiverPhone = assignment.getParcelInfo() != null ? assignment.getParcelInfo().getReceiverPhoneNumber() : "";

        String notifyReceiverSmsMessage = NotificationUtil.generateAssignmentMessgeCustomer(riderPhone, riderName, assignment.getConfirmationCode(), receiverName, parcelId);
        NotificationRequestTemplate notify = NotificationRequestTemplate.builder().body(notifyReceiverSmsMessage)
        .to(receiverPhone).build();
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

        String officeId = user.getOfficeId();
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
    public Page<DeliveryAssignments> getCancelledDeliveryAssignments(Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        String officeId = user.getOfficeId();
        if (officeId == null) {
            throw new WrongCredentialsException("User has no office assigned");
        }

        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();

        criteria.add(Criteria.where("status").is(DeliveryStatus.CANCELLED));

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

        String officeId = user.getOfficeId();
        if (officeId == null) {
            throw new WrongCredentialsException("User has no office assigned");
        }

        // Calculate time range based on period
        Long startTime = calculateStartTime(period);

        Query query = new Query();
        query.addCriteria(Criteria.where("officeId").is(officeId));

        if (startTime != null) {
            query.addCriteria(Criteria.where("createdAt").gte(startTime));
        }

        List<shortly.mandmcorp.dev.shortly.model.Reconcilations> reconciliations =
            mongoTemplate.find(query, shortly.mandmcorp.dev.shortly.model.Reconcilations.class);

        long completedCount = 0;
        long notCompletedCount = 0;
        double completedAmount = 0.0;
        double notCompletedAmount = 0.0;

        for (shortly.mandmcorp.dev.shortly.model.Reconcilations reconciliation : reconciliations) {
            if (reconciliation.isCompleted()) {
                completedCount++;
                completedAmount += reconciliation.getAmount();
            } else {
                notCompletedCount++;
                notCompletedAmount += reconciliation.getAmount();
            }
        }

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
    public List<Reconcilations> getRiderReconciliations() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Direction.DESC, "createdAt");
        return reconcilationRepository.findByRiderId(user.getUserId(), sort);
    }



}