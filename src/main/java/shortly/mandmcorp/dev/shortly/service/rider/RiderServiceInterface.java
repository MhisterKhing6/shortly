package shortly.mandmcorp.dev.shortly.service.rider;

import java.util.List;

import shortly.mandmcorp.dev.shortly.dto.request.DeliveryAssignmentRequest;
import shortly.mandmcorp.dev.shortly.dto.request.DeliveryStatusUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ReconcilationRiderRequest;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryAssignmentResponse;
import shortly.mandmcorp.dev.shortly.dto.response.ReconciliationStatsResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;
import shortly.mandmcorp.dev.shortly.model.DeliveryAssignments;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RiderServiceInterface {
    /**
     * Assigns multiple parcels to a rider and sends SMS notification.
     * 
     * @param assignmentRequest contains rider ID and list of parcel IDs
     * @return UserResponse with success message
     */
    UserResponse assignParcelsToRider(DeliveryAssignmentRequest assignmentRequest);
    
    /**
     * Retrieves delivery assignments for authenticated rider.
     * 
     * @param onlyUndelivered if true, returns only non-delivered assignments
     * @return List of delivery assignments with full parcel details
     */
    List<DeliveryAssignmentResponse> getRiderAssignments(boolean onlyUndelivered);
    
    /**
     * Updates delivery assignment status with automatic timestamp tracking.
     * 
     * @param assignmentId assignment to update
     * @param statusRequest new delivery status
     * @return UserResponse with success message
     */
    UserResponse updateDeliveryStatus(String assignmentId, DeliveryStatusUpdateRequest statusRequest);

    /**
     * Updates delivery assignment status with automatic timestamp tracking.
     * 
     * @param assignmentId assignment to update
     * @param statusRequest new delivery status
     * @return UserResponse with success message
     */
    UserResponse managerUpdateDeliveryStatus(String assignmentId, DeliveryStatusUpdateRequest statusRequest);
    
    /**
     * Searches rider's undelivered assignments by receiver phone number.
     * 
     * @param receiverPhone receiver's phone number to search
     * @return List of matching undelivered assignments
     */
    List<DeliveryAssignmentResponse> searchByReceiverPhone(String receiverPhone);

    /**
     * Gets all delivery assignments for a specific rider with payment filter.
     * 
     * @param riderId rider ID to get assignments for
     * @param payed filter by payment status (default true)
     * @return List of delivery assignments with full parcel details
     */
    List<DeliveryAssignmentResponse> getRiderAssignmentsByRiderId(String riderId, boolean payed);
    
    /**
     * Marks multiple delivery assignments as paid for reconciliation.
     * Uses bulk operations for performance. Non-existent assignment IDs are silently skipped.
     * 
     * @param reconcilationRiderRequest contains rider ID and list of assignment IDs to reconcile
     * @return UserResponse with success message
     */
    UserResponse reconcilation(ReconcilationRiderRequest reconcilationRiderRequest);

    /**
     * get all assignment by status with pagination
     * @param status delivery status to filter
     * @param pageable pagination parameters
     * @return Page of delivery assignments
     */
    Page<DeliveryAssignments> getOrderAssignmentByStatus(DeliveryStatus status, Pageable pageable);

    /**
     * resend confirmation code to receiver
     * @param assignmentId
     * @return User response
     */
    UserResponse resendConfirmationCodeToReceiver(String assignmentId);

    /**
     * get all active assignment in an office
     * @param payed : a toggle for choosing weather we shold return all payed all not
     * @return
     */
    Page<DeliveryAssignments> getAcitveAssignments(Pageable pageable, boolean payed);

    /**
     * Gets all cancelled delivery assignments in the user's office.
     *
     * @param pageable pagination parameters
     * @return Page of cancelled delivery assignments sorted by assignedAt descending
     */
    Page<DeliveryAssignments> getCancelledDeliveryAssignments(Pageable pageable);

    /**
     * Gets reconciliation statistics for the user's office.
     *
     * @param period time period filter: "day", "week", "month", "year", or "all" (default: "day")
     * @return ReconciliationStatsResponse with completed and not completed counts and amounts
     */
    ReconciliationStatsResponse getReconciliationStats(String period);

}