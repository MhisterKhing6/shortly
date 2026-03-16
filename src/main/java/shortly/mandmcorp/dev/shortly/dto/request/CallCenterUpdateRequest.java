package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.CallCenterCallOutCome;

@Data
public class CallCenterUpdateRequest {
    @NotNull(message = "Call outcome is required")
    private CallCenterCallOutCome callOutCome;
    private String remark;
}
