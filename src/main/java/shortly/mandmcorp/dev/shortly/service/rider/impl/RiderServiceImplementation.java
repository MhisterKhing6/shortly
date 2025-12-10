package shortly.mandmcorp.dev.shortly.service.rider.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.dto.request.DeliveryAssignmentRequest;
import shortly.mandmcorp.dev.shortly.dto.request.DeliveryStatusUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryAssignmentResponse;
import shortly.mandmcorp.dev.shortly.dto.response.ParcelResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;
import shortly.mandmcorp.dev.shortly.exceptions.EntityNotFound;
import shortly.mandmcorp.dev.shortly.exceptions.WrongCredentialsException;
import shortly.mandmcorp.dev.shortly.model.DeliveryAssignments;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.DeliveryAssignmentsRepository;
import shortly.mandmcorp.dev.shortly.repository.ParcelRepository;
import shortly.mandmcorp.dev.shortly.repository.UserRepository;
import shortly.mandmcorp.dev.shortly.utils.ParcelMapper;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationInterface;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationRequestTemplate;
import shortly.mandmcorp.dev.shortly.service.rider.RiderServiceInterface;
import shortly.mandmcorp.dev.shortly.utils.NotificationUtil;



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

    public RiderServiceImplementation(DeliveryAssignmentsRepository deliveryAssignmentsRepository, UserRepository userRepository, ParcelRepository parcelRepository, 
        @Qualifier("smsNotification") NotificationInterface notification, ParcelMapper parcelMapper) {
        this.deliveryAssignmentsRepository = deliveryAssignmentsRepository;
        this.userRepository = userRepository;
        this.parcelRepository = parcelRepository;
        this.notification = notification;
        this.parcelMapper = parcelMapper;
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
        
        for(String parcelId : assignmentRequest.getParcelIds()) {
            Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new EntityNotFound("Parcel not found: " + parcelId));
            
            DeliveryAssignments assignment = new DeliveryAssignments();
            assignment.setRiderId(rider);
            assignment.setOrderId(parcel);
            assignment.setStatus(DeliveryStatus.ASSIGNED);
            assignment.setAssignedAt(assignedAt);
            parcel.setParcelAssigned(true);
            deliveryAssignmentsRepository.save(assignment);
            parcelRepository.save(parcel);
            
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
            ? deliveryAssignmentsRepository.findByRiderIdAndStatusNot(rider, DeliveryStatus.DELIVERED)
            : deliveryAssignmentsRepository.findByRiderId(rider);
        
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
        
        if(!assignment.getRiderId().getUserId().equals(rider.getUserId())) {
            throw new WrongCredentialsException("Not authorized to update this assignment");
        }
        
        assignment.setStatus(statusRequest.getStatus());
        if(statusRequest.getStatus() == DeliveryStatus.ACCEPTED) {
            assignment.setAcceptedAt(System.currentTimeMillis());
        } else if(statusRequest.getStatus() == DeliveryStatus.DELIVERED) {
            assignment.setCompletedAt(System.currentTimeMillis());
            assignment.getOrderId().setDelivered(true);
            parcelRepository.save(assignment.getOrderId());
            //send sms to rider
            Parcel parcel = assignment.getOrderId();
            String message = NotificationUtil.generateParcelStatusUpdateMsg(parcel.getParcelId(), "DELIVERED");
            NotificationRequestTemplate notify = NotificationRequestTemplate.builder().body(message).to(parcel.getDriver().getPhoneNumber()).build();
            notification.send(notify);
            parcel.setDelivered(true);
            parcelRepository.save(parcel);
        }
        else if(statusRequest.getStatus() == DeliveryStatus.CANCELLED) {
            assignment.getOrderId().setDelivered(false);
            assignment.getOrderId().setParcelAssigned(false);
            parcelRepository.save(assignment.getOrderId());
        }
        deliveryAssignmentsRepository.save(assignment);
        return new UserResponse("Delivery status updated successfully", rider.getPhoneNumber());
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
        List<DeliveryAssignments> assignments = deliveryAssignmentsRepository.findByRiderAndReceiverPhoneAndNotDelivered(rider, receiverPhone);
        
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
        response.setRiderName(assignment.getRiderId().getName());
        response.setParcel(parcelMapper.toResponse(assignment.getOrderId(), assignment.getOrderId().getDriver(), 
                                                  assignment.getOrderId().getSender(), assignment.getOrderId().getReceiver()));
        response.setStatus(assignment.getStatus());
        response.setAssignedAt(assignment.getAssignedAt());
        response.setAcceptedAt(assignment.getAcceptedAt());
        response.setCompletedAt(assignment.getCompletedAt());
        return response;
    }
}