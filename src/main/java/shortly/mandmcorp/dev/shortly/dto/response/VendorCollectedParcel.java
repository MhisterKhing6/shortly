package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VendorCollectedParcel {
    private String parcelId;
    private String receiverName;
    private String stationName;
    private double itemCost;
    private double deliveryFee;
    private double total;
    private Long createdAt;
}
