package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TrackingAssignDriverRequest {
    @NotBlank(message = "Rider ID is required")
    private String riderId;
}
