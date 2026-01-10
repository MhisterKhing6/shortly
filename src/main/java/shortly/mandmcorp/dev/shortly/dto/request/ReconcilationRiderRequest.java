package shortly.mandmcorp.dev.shortly.dto.request;

import lombok.Data;

@Data
public class ReconcilationRiderRequest {
    String assignmentId;
    Long reconciledAt;
    Double payedAmount;
}
