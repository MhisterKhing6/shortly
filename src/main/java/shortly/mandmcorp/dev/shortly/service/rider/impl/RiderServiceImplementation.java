package shortly.mandmcorp.dev.shortly.service.rider.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
import shortly.mandmcorp.dev.shortly.enums.UserRole;
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
        User officeManager = null;
        List<User>  managers = userRepository.findByRoleAndOfficeId(UserRole.MANAGER, rider.getOfficeId());
        if(!managers.isEmpty()) {
            officeManager = managers.get(0);
        } else {
            officeManager = rider;
        }
         RiderInfo riderInfo = RiderInfo.builder()
                .riderId(rider.getUserId())
                .riderName(rider.getName())
                .riderPhoneNumber(rider.getPhoneNumber())
                .build();
        long assignedAt = System.currentTimeMillis();
        String confirmationCode = "";
        DeliveryAssignments assignment = new DeliveryAssignments();
        assignment.setRiderInfo(riderInfo);
        assignment.setOfficeId(rider.getOfficeId());
        assignment.setStatus(DeliveryStatus.ASSIGNED);
        assignment.setConfirmationCode(confirmationCode);
        assignment.setAssignedAt(assignedAt);

        List<ParcelInfo> parcelInfos = new ArrayList<>();
        for(String parcelId : assignmentRequest.getParcelIds()) {
            Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new EntityNotFound("Parcel not found: " + parcelId));
            if(!parcel.isHasCalled() ||!parcel.isHomeDelivery()) {
                log.warn("Parcel {} has not been called. Skipping assignment.", parcelId);
                continue;
            }
            confirmationCode = OtpUtil.generateOtp();

            // Create embedded RiderInfo
           
            double parcelAmount = parcel.getDeliveryCost() + parcel.getInboundCost();
            // Create embedded ParcelInfo
            ParcelInfo parcelInfo = ParcelInfo.builder()
                .parcelId(parcel.getParcelId())
                .parcelDescription(parcel.getParcelDescription())
                .receiverName(parcel.getReceiverName())
                .receiverPhoneNumber(parcel.getRecieverPhoneNumber())
                .receiverAddress(parcel.getReceiverAddress())
                .senderName(parcel.getSenderName())
                .parcelAmount(parcelAmount)
                .senderPhoneNumber(parcel.getSenderPhoneNumber())
                .build();
            parcelInfos.add(parcelInfo);
            assignment.setAmount(assignment.getAmount() + parcelAmount);
            parcel.setParcelAssigned(true);
            deliveryAssignmentsRepository.save(assignment);
            parcelRepository.save(parcel);

        String notifyReceiverSmsMessage = NotificationUtil.generateAssignmentMessgeCustomer(officeManager.getPhoneNumber(), rider.getName(), confirmationCode, parcel.getReceiverName(), parcel.getParcelId());
        NotificationRequestTemplate notify = NotificationRequestTemplate.builder().body(notifyReceiverSmsMessage)
        .to(parcel.getRecieverPhoneNumber()).build();
        notification.send(notify);
            
        }
        assignment.setParcels(parcelInfos);
        deliveryAssignmentsRepository.save(assignment);
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

            // Fetch and update all parcels using parcelIds from embedded ParcelInfo list
            if(assignment.getParcels() != null && !assignment.getParcels().isEmpty()) {
                for(ParcelInfo parcelInfo : assignment.getParcels()) {
                    String parcelId = parcelInfo.getParcelId();
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
                }
            }

            assignment.setStatus(DeliveryStatus.DELIVERED);
            assignment.setPayed(true);
            assignment.setPayementMethod(statusRequest.getPayementMethod());
        }
        else if(statusRequest.getStatus() == DeliveryStatus.CANCELLED) {
            assignment.setCancelationReason(statusRequest.getCancelationReason());

            if(assignment.getParcels() != null && !assignment.getParcels().isEmpty() && statusRequest.getParcelId() != null) {
                ParcelInfo parcelToCancel = null;

                // Find the parcel to be cancelled
                for(ParcelInfo parcelInfo : assignment.getParcels()) {
                    if(parcelInfo.getParcelId().equals(statusRequest.getParcelId())) {
                        parcelToCancel = parcelInfo;
                        break;
                    }
                }

                if(parcelToCancel != null && !parcelToCancel.isCancelled()) {
                    // Update the parcel in the database
                    Parcel parcel = parcelRepository.findById(parcelToCancel.getParcelId())
                        .orElseThrow(() -> new EntityNotFound("Parcel not found"));

                    parcel.setCancelationCount(parcel.getCancelationCount() + 1);
                    parcel.setDelivered(false);
                    parcel.setParcelAssigned(false);
                    parcelRepository.save(parcel);

                    // Subtract the parcel amount from the assignment total
                    double parcelAmount = parcelToCancel.getParcelAmount();
                    assignment.setAmount(assignment.getAmount() - parcelAmount);

                    // Mark the parcel as cancelled in the embedded list
                    parcelToCancel.setCancelled(true);

                    // Check if all parcels are cancelled, if so set assignment status to CANCELLED
                    boolean allCancelled = assignment.getParcels().stream()
                        .allMatch(ParcelInfo::isCancelled);

                    if(allCancelled) {
                        assignment.setStatus(DeliveryStatus.CANCELLED);
                    }
                }
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

            // Fetch and update all parcels using parcelIds from embedded ParcelInfo list
            if(assignment.getParcels() != null && !assignment.getParcels().isEmpty()) {
                for(ParcelInfo parcelInfo : assignment.getParcels()) {
                    String parcelId = parcelInfo.getParcelId();
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
                }
            }

            assignment.setStatus(DeliveryStatus.DELIVERED);
            assignment.setPayed(true);
        }
        else if(statusRequest.getStatus() == DeliveryStatus.CANCELLED) {
            // Mark the specific parcel as cancelled and update parcel status
            assignment.setCancelationReason(statusRequest.getCancelationReason());

            if(assignment.getParcels() != null && !assignment.getParcels().isEmpty() && statusRequest.getParcelId() != null) {
                ParcelInfo parcelToCancel = null;

                for(ParcelInfo parcelInfo : assignment.getParcels()) {
                    if(parcelInfo.getParcelId().equals(statusRequest.getParcelId())) {
                        parcelToCancel = parcelInfo;
                        break;
                    }
                }

                if(parcelToCancel != null && !parcelToCancel.isCancelled()) {
                    Parcel parcel = parcelRepository.findById(parcelToCancel.getParcelId())
                        .orElseThrow(() -> new EntityNotFound("Parcel not found"));

                    parcel.setCancelationCount(parcel.getCancelationCount() + 1);
                    parcel.setDelivered(false);
                    parcel.setParcelAssigned(false);
                    parcelRepository.save(parcel);

                    double parcelAmount = parcelToCancel.getParcelAmount();
                    assignment.setAmount(assignment.getAmount() - parcelAmount);

                    parcelToCancel.setCancelled(true);

                    boolean allCancelled = assignment.getParcels().stream()
                        .allMatch(ParcelInfo::isCancelled);

                    if(allCancelled) {
                        assignment.setStatus(DeliveryStatus.CANCELLED);
                    }
                }
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

        DeliveryAssignments assignment = deliveryAssignmentsRepository.findById(reconcilationRiderRequest.getAssignmentId()).
        orElseThrow(() -> new EntityNotFound("Assignment not found" ));

    Reconcilations reconcilation = reconcilationRepository.findByAssignmentId(reconcilationRiderRequest.getAssignmentId())
            .orElse(new Reconcilations());

    if (assignment != null) {
        if (assignment.getParcels() != null && !assignment.getParcels().isEmpty()) {
            for (ParcelInfo parcelInfo : assignment.getParcels()) {
                if (!parcelInfo.isCancelled() && parcelInfo.getParcelId() != null) {
                    Parcel parcel = parcelRepository.findById(parcelInfo.getParcelId()).orElse(null);
                    if (parcel != null) {
                        parcel.setDelivered(true);
                        parcelRepository.save(parcel);
                    }
                }
            }
        }

        reconcilation.setAssignmentId(assignment.getAssignmentId());
        reconcilation.setPayedTo(frontDesk.getUserId());
        reconcilation.setType(ReconcilationType.RIDER);
        reconcilation.setExpectedAmount(assignment.getAmount());
        reconcilation.setRiderId(assignment.getRiderInfo() != null ? assignment.getRiderInfo().getRiderId() : null);
        reconcilation.setRiderName(assignment.getRiderInfo() != null ? assignment.getRiderInfo().getRiderName() : null);
        reconcilation.setRiderPhoneNumber(assignment.getRiderInfo() != null ? assignment.getRiderInfo().getRiderPhoneNumber() : null);
        reconcilation.setOfficeId(assignment.getOfficeId());
        reconcilation.setCompleted(true);
        reconcilation.setReconciledAt(reconciledAtTimestamp);
        reconcilation.setPayedAmount(reconcilationRiderRequest.getPayedAmount());

        reconcilationRepository.save(reconcilation);
        assignment.setPayed(true);
        assignment.setStatus(DeliveryStatus.COMPLETED);
        deliveryAssignmentsRepository.save(assignment);
    }

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