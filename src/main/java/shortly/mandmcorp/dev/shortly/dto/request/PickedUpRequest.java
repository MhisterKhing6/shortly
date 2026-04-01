package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PickedUpRequest {
    @NotBlank(message = "Parcel ID is required")
    private String parcelId;
    private String whoPickedUpTelephoneNumber;
    private String whoPickedUpName;
}
