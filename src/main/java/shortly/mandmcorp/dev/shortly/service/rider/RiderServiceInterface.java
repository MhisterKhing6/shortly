package shortly.mandmcorp.dev.shortly.service.rider;

import java.util.List;

import shortly.mandmcorp.dev.shortly.dto.request.DeliveryAssignmentRequest;
import shortly.mandmcorp.dev.shortly.dto.request.DeliveryStatusUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ReconcilationRiderRequest;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryAssignmentResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;

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
     * get all assignment
     */
    List<DeliveryAssignmentResponse> getOrderAssignmentByStatus(DeliveryStatus status) ;

    /**
     * resend confirmation code to receiver
     * @param assignmentId
     * @return User response
     */
    UserResponse resendConfirmationCodeToReceiver(String assignmentId);

}