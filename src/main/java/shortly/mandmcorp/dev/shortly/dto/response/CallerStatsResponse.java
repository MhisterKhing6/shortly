package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallerStatsResponse {
    private String callerPhoneNumber;
    private String period; // "all" or "month"
    private long totalCalls;
    private long homeDeliveredCalls;
    private long nonHomeDeliveredCalls;
}
