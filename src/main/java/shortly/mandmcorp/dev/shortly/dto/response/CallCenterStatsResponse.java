package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class CallCenterStatsResponse {
    private long totalDeliveredYesterday;
    private long reached;
    private long unreachable;
    private long notCalled;
}
