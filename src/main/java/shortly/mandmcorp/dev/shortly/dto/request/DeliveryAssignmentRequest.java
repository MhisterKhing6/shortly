package shortly.mandmcorp.dev.shortly.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class DeliveryAssignmentRequest {
    @NotBlank(message = "Rider ID is required")
    private String riderId;
    
    @NotEmpty(message = "Parcel IDs list cannot be empty")
    private List<String> parcelIds;

    /** Optional planned route. parcelIds arrive in planned order. Ignored (but the assignment still happens) if invalid. */
    private shortly.mandmcorp.dev.shortly.model.RoutePlan routePlan;
}