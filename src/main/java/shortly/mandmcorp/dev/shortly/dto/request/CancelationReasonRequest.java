package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CancelationReasonRequest {
    @NotBlank(message="Reason is required")
    private  String reason;
}
