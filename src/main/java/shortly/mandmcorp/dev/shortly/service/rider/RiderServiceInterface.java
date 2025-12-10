package shortly.mandmcorp.dev.shortly.service.rider;

import java.util.List;

import shortly.mandmcorp.dev.shortly.dto.request.DeliveryAssignmentRequest;
import shortly.mandmcorp.dev.shortly.dto.request.DeliveryStatusUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryAssignmentResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;

public interface RiderServiceInterface {
    UserResponse assignParcelsToRider(DeliveryAssignmentRequest assignmentRequest);
    List<DeliveryAssignmentResponse> getRiderAssignments(boolean onlyUndelivered);
    UserResponse updateDeliveryStatus(String assignmentId, DeliveryStatusUpdateRequest statusRequest);
    List<DeliveryAssignmentResponse> searchByReceiverPhone(String receiverPhone);
}