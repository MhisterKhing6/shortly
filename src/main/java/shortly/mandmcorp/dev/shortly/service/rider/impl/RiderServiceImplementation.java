package shortly.mandmcorp.dev.shortly.service.rider.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

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
    private final DeliveryAssignmentsRepository deliveryRepository;
    private final ReconcilationRepository reconcilationRepository;

    public RiderServiceImplementation(DeliveryAssignmentsRepository deliveryAssignmentsRepository, UserRepository userRepository, ParcelRepository parcelRepository, 
        @Qualifier("smsNotification") NotificationInterface notification, ParcelMapper parcelMapper, MongoTemplate mongoTemplate, 
        DeliveryAssignmentsRepository deliveryRepo, ReconcilationRepository reconcilationRepository  ) {
        this.deliveryAssignmentsRepository = deliveryAssignmentsRepository;
        this.userRepository = userRepository;
        this.parcelRepository = parcelRepository;
        this.notification = notification;
        this.parcelMapper = parcelMapper;
        this.mongoTemplate = mongoTemplate;
        this.deliveryRepository = deliveryRepo;
        this.reconcilationRepository = reconcilationRepository;
    }

    /**
     * Generates a daily assignment ID in the format: {riderId}_{YYYYMMDD}
     * All assignments for the same rider on the same day will share this ID.
     *
     * @param riderId the rider's user ID
     * @param timestamp the assignment timestamp in milliseconds
     * @return formatted assignment ID
     */
    private String generateDailyAssignmentId(String riderId, long timestamp) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String dateStr = dateFormat.format(new Date(timestamp));
        return riderId + "_" + dateStr;
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
        log.info("Assigning {} parcels to rider: {}", assignmentRequest.getParcelIds().size(), assignmentRequest.getRiderId());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        
        User frontDesk = (User) auth.getPrincipal();
        User rider = userRepository.findById(assignmentRequest.getRiderId())
            .orElseThrow(() -> new EntityNotFound("Rider not found"));

        // Get the first office ID from rider's office list
        String riderOfficeId = (rider.getOfficeIds() != null && !rider.getOfficeIds().isEmpty())
            ? rider.getOfficeIds().get(0)
            : null;

        if(riderOfficeId == null) {
            throw new EntityNotFound("Rider has no assigned office");
        }

        User officeManager = null;
        List<User> managers = userRepository.findByRoleAndOfficeIdsContaining(UserRole.MANAGER, riderOfficeId);
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

        // Generate daily assignment ID: {riderId}_{YYYYMMDD}
        String dailyAssignmentId = generateDailyAssignmentId(rider.getUserId(), assignedAt);

        DeliveryAssignments assignment = deliveryAssignmentsRepository.findById(dailyAssignmentId)
                .orElse(null);

        boolean isNewAssignment = (assignment == null);

        String officeId = frontDesk != null ? frontDesk.getOfficeIds().get(0) : riderOfficeId;

        if (isNewAssignment) {
            assignment = new DeliveryAssignments();
            assignment.setAssignmentId(dailyAssignmentId);
            assignment.setRiderInfo(riderInfo);
            assignment.setOfficeId(officeId);
            assignment.setStatus(DeliveryStatus.ASSIGNED);
            assignment.setConfirmationCode(confirmationCode);
            assignment.setAssignedAt(assignedAt);
            assignment.setCreatedAt(assignedAt);
            assignment.setParcels(new ArrayList<>());
            assignment.setAmount(0.0);
        } else {
            log.info("Found existing assignment for rider {} on date {}. Adding parcels to it.", rider.getUserId(), dailyAssignmentId);
        }

        // Get existing parcels list or create new one
        List<ParcelInfo> existingParcels = assignment.getParcels();
        if (existingParcels == null) {
            existingParcels = new ArrayList<>();
        }

        List<ParcelInfo> newParcels = new ArrayList<>();
        for(String parcelId : assignmentRequest.getParcelIds()) {
            Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new EntityNotFound("Parcel not found: " + parcelId));
                parcel.setRiderInfo(riderInfo);
                if(!parcel.isHasCalled() ||!parcel.isHomeDelivery()) {
                if(assignmentRequest.getParcelIds().size() == 1) {
                    throw new EntityNotFound("Parcel has not been called or is not for home delivery: " + parcelId);
                }
                log.warn("Parcel {} has not been called. Skipping assignment.", parcelId);
                continue;
            }

            if(parcel.isParcelAssigned() || parcel.isDelivered()) {
                log.warn("Parcel {} is already assigned. Skipping assignment.", parcelId);
                if(parcel.getRiderId() != null && parcel.getRiderId().equals(rider.getUserId())) {
                    log.info("Parcel {} is already assigned to the same rider {}. Skipping exception.", parcelId, rider.getUserId());
                    if(assignmentRequest.getParcelIds().size() == 1) {
                        throw new EntityNotFound("Parcel is already assigned to the same rider: " + parcelId);
                    }
                    continue;
                }
                
            }

            confirmationCode = OtpUtil.generateOtp();
            double parcelAmount = parcel.getDeliveryCost() + parcel.getInboundCost();
            ParcelInfo parcelInfo = ParcelInfo.builder()
                .parcelId(parcel.getParcelId())
                .parcelDescription(parcel.getParcelDescription())
                .receiverName(parcel.getReceiverName())
                .receiverPhoneNumber(parcel.getRecieverPhoneNumber())
                .receiverAddress(parcel.getReceiverAddress())
                .senderName(parcel.getSenderName())
                .parcelAmount(parcelAmount)
                .inboundCost(parcel.getInboundCost())
                .deliveryCost(parcel.getDeliveryCost())
                .senderPhoneNumber(parcel.getSenderPhoneNumber())
                .build();
            newParcels.add(parcelInfo);
            assignment.setInboundCost(assignment.getInboundCost() + parcel.getInboundCost());
            assignment.setDeliveryCost(assignment.getDeliveryCost() + parcel.getDeliveryCost());
            parcel.setParcelAssigned(true);
            parcelRepository.save(parcel);

            String notifyReceiverSmsMessage = NotificationUtil.generateAssignmentMessgeCustomer(officeManager.getPhoneNumber(), rider.getName(), confirmationCode, parcel.getReceiverName(), parcel.getParcelId());
            NotificationRequestTemplate notify = NotificationRequestTemplate.builder().body(notifyReceiverSmsMessage)
                .to(parcel.getRecieverPhoneNumber()).build();
            notification.send(notify);
        }

        existingParcels.addAll(newParcels);
        assignment.setParcels(existingParcels);
        assignment.setUpdatedAt(System.currentTimeMillis());
        assignment.setAmount(assignment.getDeliveryCost() + assignment.getInboundCost());
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
    public DeliveryAssignments updateDeliveryStatus(String assignmentId, DeliveryStatusUpdateRequest statusRequest) {
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

        if(statusRequest.getStatus() == DeliveryStatus.DELIVERED) {
           /* if( !statusRequest.getConfirmationCode().equals(assignment.getConfirmationCode())) {
                throw new ActionNotAllowed("Invalid  confirmation code");
            } */

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
                        selectedParcel.setDelivered(true);
                        selectedParcel.setPaymentMethod(statusRequest.getPayementMethod());

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
                    parcelRepository.save(parcel);

                    double parcelAmount = parcelToCancel.getParcelAmount();
                    assignment.setAmount(assignment.getAmount() - parcelAmount);
                    assignment.setDeliveryCost(assignment.getDeliveryCost() - parcelToCancel.getDeliveryCost());
                    assignment.setInboundCost(assignment.getInboundCost() - parcelToCancel.getInboundCost());

                    parcelToCancel.setReturned(true);

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
                        selectedParcel.setDelivered(true);
                        selectedParcel.setPaymentMethod(statusRequest.getPayementMethod());
                        
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

        assignment.setPayed(true);
        assignment.setPayedAt(reconciledAtTimestamp);
        assignment.setPayedTo(frontDesk.getUserId());
        double currentPayedAmount = assignment.getAmountPayed() + reconcilationRiderRequest.getPayedAmount();
        assignment.setAmountPayed(currentPayedAmount);
        assignment.setUpdatedAt(System.currentTimeMillis());
        deliveryAssignmentsRepository.save(assignment);
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

        // Update assignment fields if provided
        if (updateRequest.getRiderInfo() != null) {
            assignment.setRiderInfo(updateRequest.getRiderInfo());
        }

        if (updateRequest.getStatus() != null) {
            assignment.setStatus(updateRequest.getStatus());
        }

        if (updateRequest.getReturnReason() != null) {
            assignment.setReturnReason(updateRequest.getReturnReason());
        }

        if (updateRequest.getPayementMethod() != null) {
            assignment.setPayementMethod(updateRequest.getPayementMethod());
        }

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

                    parcelRepository.save(parcel);

                    // Replace the ParcelInfo in the assignment
                    assignment.getParcels().set(parcelIndex, updatedParcelInfo);
                }
            }
        }

        // Save the updated assignment
        deliveryAssignmentsRepository.save(assignment);

        log.info("Successfully updated delivery assignment: {}", updateRequest.getAssignmentId());
        return new UserResponse("Delivery assignment updated successfully", user.getPhoneNumber());
    }


}