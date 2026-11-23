package shortly.mandmcorp.dev.shortly.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One delivered parcel's amounts as recorded by the manager at reconciliation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationLine {
    private String parcelId;
    private Double systemAmount;
    private Double collectedAmount;
    private Double variance;
    private String varianceReason;
    private String varianceNote;
}
