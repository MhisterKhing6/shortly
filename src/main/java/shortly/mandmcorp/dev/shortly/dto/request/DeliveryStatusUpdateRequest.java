package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;

@Data
public class DeliveryStatusUpdateRequest {
    @NotNull(message = "Delivery status is required")
    private DeliveryStatus status;
    private String cancelationReason;
    private String confirmationCode;
    private String payementMethod;
    private String parcelId;
}