package shortly.mandmcorp.dev.shortly.dto.request;

import lombok.Data;

/** Body of POST /api-frontdesk/counter-cashup. Expected figures are recomputed on the server. */
@Data
public class CounterCashupRequest {
    private String date;
    private Double expectedCash;
    private Double countedCash;
    private Double expectedMomo;
    /** Required when countedCash differs from the server's expected cash. */
    private String note;
}
