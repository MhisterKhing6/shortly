package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.RiderStatus;

@Data
public class RiderStatusUpdateRequest {
    @NotNull(message = "Rider status is required")
    private RiderStatus riderStatus;
}