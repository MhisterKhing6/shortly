package shortly.mandmcorp.dev.shortly.dto.request;

import lombok.Data;

@Data
public class PickupChargeRequest {
    private Boolean pickupChargeEnabled;
    /** GHC; required and > 0 when enabled. */
    private Double pickupChargeAmount;
}
