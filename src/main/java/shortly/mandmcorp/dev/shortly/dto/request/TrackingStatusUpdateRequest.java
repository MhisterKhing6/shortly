package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.ParcelStatus;

@Data
public class TrackingStatusUpdateRequest {
    @NotNull(message = "Parcel status is required")
    private ParcelStatus parcelStatus;
    private String notes;
}
