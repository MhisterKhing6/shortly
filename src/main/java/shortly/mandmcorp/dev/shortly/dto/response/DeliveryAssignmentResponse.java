package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;
import shortly.mandmcorp.dev.shortly.model.Parcel;

@Data
public class DeliveryAssignmentResponse {
    private String assignmentId;
    private String riderName;
    private Parcel parcel;
    private DeliveryStatus status;
    private long assignedAt;
    private long acceptedAt;
    private long completedAt;
}