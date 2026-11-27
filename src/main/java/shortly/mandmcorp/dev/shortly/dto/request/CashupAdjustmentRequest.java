package shortly.mandmcorp.dev.shortly.dto.request;

import lombok.Data;

@Data
public class CashupAdjustmentRequest {
    private String date;
    /** Positive adds cash to the day, negative removes it. */
    private Double amount;
    private String reason;
}
