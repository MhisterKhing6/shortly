package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.User;

@Data
public class DeliveryAssignmentResponse {
    private String assignmentId;
    private String riderName;
    private String riderId;
    private User rider;
    private Parcel parcel;
    private DeliveryStatus status;
    private long assignedAt;
    private long acceptedAt;
    private long completedAt;
}