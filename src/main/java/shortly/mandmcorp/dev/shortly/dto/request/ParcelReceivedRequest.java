package shortly.mandmcorp.dev.shortly.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class ParcelReceivedRequest {

    @NotEmpty(message = "At least one parcel ID is required")
    private List<String> parcelIds;
}
