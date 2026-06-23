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
public class RiderTrackingResponse {
    private String parcelId;
    private String parcelDescription;
    private ParcelStatus parcelStatus;
    private ParcelTypes typeofParcel;
    private boolean isDelivered;
    private boolean isPOD;
    private boolean isFragile;
    private boolean homeDelivery;
    private String receiverName;
    private String receiverAddress;
    private String recieverPhoneNumber;
    private String alternativePhoneNumber;
    private String pickupAddress;
    private String pickupContactName;
    private String pickupContactPhoneNumber;
    private String pickupInstructions;
    private String deliveryAddress;
    private String deliveryContactName;
    private String deliveryContactPhoneNumber;
    private String specialInstructions;
    private double deliveryCost;
    private double pickUpCost;
    private Long createdAt;
    private Long updatedAt;
}
