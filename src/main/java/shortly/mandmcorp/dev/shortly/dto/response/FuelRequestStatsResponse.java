package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FuelRequestStatsResponse {
    private long total;
    private long approved;
    private long pending;
    private long rejected;
}
