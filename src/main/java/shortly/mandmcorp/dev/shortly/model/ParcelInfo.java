package shortly.mandmcorp.dev.shortly.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import shortly.mandmcorp.dev.shortly.enums.ParcelTypes;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParcelInfo {
    private String parcelId;
    private String receiverName;
    private String receiverPhoneNumber;
    private String alternativePhoneNumber;
    private String receiverAddress;
    private String senderName;
    private String senderPhoneNumber;
    private double parcelAmount;
    private boolean payed;
    private boolean returned;
    private boolean delivered;
    private String paymentMethod;
    private double inboundCost;
    private double deliveryCost;

    private boolean isPOD;
    private boolean isFragile;
    private double storageCost;
    private double pickUpCost;
    private boolean pickedUp;
    private boolean homeDelivery;

    private String vehicleNumber;
    private String driverName;
    private String driverPhoneNumber;
    private String driverId;

    private String officeId;
    private String shelfName;
    private boolean inboudPayed;
    private String shelfId;

    @Builder.Default
    private ParcelTypes typeofParcel = ParcelTypes.PARCEL;

    // For online
    private double ItemCost;

    // For pickup
    private String pickupAddress;
    private String pickupContactName;
    private String pickupContactPhoneNumber;
    private String pickupInstructions;
    private String deliveryAddress;
    private String deliveryContactName;
    private String deliveryContactPhoneNumber;
    private String specialInstructions;
}
