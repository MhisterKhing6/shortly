package shortly.mandmcorp.dev.shortly.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;
import shortly.mandmcorp.dev.shortly.model.ParcelInfo;
import shortly.mandmcorp.dev.shortly.model.RiderInfo;

@Data
public class DeliveryAssignmentUpdateRequest {
    @NotNull(message = "Assignment ID is required")
    private String assignmentId;

    private RiderInfo riderInfo;
    private List<ParcelInfo> parcels;
    private DeliveryStatus status;
    private String returnReason;
    private String payementMethod;
    private boolean payed;
    private double amount;
    private double inboundCost;
    private double deliveryCost;
}
