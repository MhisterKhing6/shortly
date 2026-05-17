package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssignDeviceRequest {
    @NotBlank
    private String phoneNumber;
    @NotBlank
    private String deviceImei;
}
