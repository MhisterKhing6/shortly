package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One station-day in the admin cash-up report; closed=false means counter money with no cash-up yet. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterCashupReportRow {
    private String officeId;
    private String officeName;
    private String date;
    private boolean closed;
    private int collectionCount;
    private Double expectedCash;
    private Double countedCash;
    private Double difference;
    private Double expectedMomo;
    private Double adjustmentTotal;
    private String note;
    private String closedByName;
    private Long closedAt;
}
