package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.Builder;
import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.ParcelStatus;

@Data
@Builder
public class VendorParcelItem {
    private String parcelId;
    private String receiverName;
    private String recieverPhoneNumber;
    private String parcelDescription;
    private double deliveryFee;
    private double itemCost;
    private ParcelStatus parcelStatus;
    private boolean isPOD;
    private Long createdAt;
}
