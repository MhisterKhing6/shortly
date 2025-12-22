package shortly.mandmcorp.dev.shortly.dto.request;

import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;

@Data
public class UpdateParcelRequest {

    private String reasonId;
    private String confirmationCode;
    private String assignmentId;
    private DeliveryStatus status;
}
