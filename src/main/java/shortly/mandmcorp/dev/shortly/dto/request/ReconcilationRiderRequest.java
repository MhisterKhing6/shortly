package shortly.mandmcorp.dev.shortly.dto.request;

import java.util.List;

import lombok.Data;
import shortly.mandmcorp.dev.shortly.model.ReconciliationLine;

@Data
public class ReconcilationRiderRequest {
    String assignmentId;
    Long reconciledAt;
    /** What the rider actually handed over (expectedAmount + varianceAmount). */
    Double payedAmount;
    /** What the system expected for the assignment, as computed on the screen. */
    Double expectedAmount;
    /** Sum of line variances. Negative = short, positive = over. */
    Double varianceAmount;
    /** One per delivered parcel. */
    List<ReconciliationLine> lines;
}
