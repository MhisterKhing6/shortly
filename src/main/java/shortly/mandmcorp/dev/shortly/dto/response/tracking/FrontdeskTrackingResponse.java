package shortly.mandmcorp.dev.shortly.dto.response.tracking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import shortly.mandmcorp.dev.shortly.enums.ParcelStatus;
import shortly.mandmcorp.dev.shortly.enums.ParcelTypes;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrontdeskTrackingResponse {
    private String parcelId;
    private String parcelDescription;
    private ParcelStatus parcelStatus;
    private ParcelTypes typeofParcel;
    private boolean isDelivered;
    private boolean isParcelAssigned;
    private boolean isPOD;
    private boolean isFragile;
    private boolean homeDelivery;
    private String senderName;
    private String senderPhoneNumber;
    private String receiverName;
    private String receiverAddress;
    private String recieverPhoneNumber;
    private String alternativePhoneNumber;
    private String shelfName;
    private String shelfId;
    private String driverName;
    private String driverPhoneNumber;
    private String driverId;
    private String officeId;
    private double deliveryCost;
    private double inboundCost;
    private double storageCost;
    private String paymentMethod;
    private Long createdAt;
    private Long updatedAt;
}
