package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LocationRequest {
    @NotBlank(message = "Location name is required")
    private String name;
    
    @NotBlank(message = "Region is required")
    private String region;
    
    @NotBlank(message = "Country is required")
    private String country;
}