package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OfficeRequest {
    @NotBlank(message = "Office name is required")
    private String name;
    
    private String address;
    private String phoneNumber;
    private String managerId;
    
    @NotBlank(message = "Location ID is required")
    private String locationId;
}