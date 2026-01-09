package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReconciliationStatsResponse {
    private long completedCount;
    private long notCompletedCount;
    private double completedAmount;
    private double notCompletedAmount;
    private double totalAmount;
    private long totalCount;
}
