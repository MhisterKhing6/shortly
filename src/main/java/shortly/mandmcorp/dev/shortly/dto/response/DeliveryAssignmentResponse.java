package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;

@Data
public class DeliveryAssignmentResponse {
    private String assignmentId;
    private String riderName;
    private ParcelResponse parcel;
    private DeliveryStatus status;
    private long assignedAt;
    private long acceptedAt;
    private long completedAt;
}