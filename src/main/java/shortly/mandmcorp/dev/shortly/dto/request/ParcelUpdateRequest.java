package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.ParcelTypes;

@Data
public class ParcelUpdateRequest {
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number format")
    private String driverPhoneNumber;
    private String driverName;
    private String vehicleNumber;
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number format")
    private String senderPhoneNumber;
    private String senderName;
    private String receiverAddress;
    private String receiverName;
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number format")
    private String recieverPhoneNumber;
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number format")
    private String alternativePhoneNumber;
    private String parcelDescription;
    private Boolean isPOD;
    private Boolean isDelivered;
    private Boolean isParcelAssigned;
    private Double inboundCost;
    private Double pickUpCost;
    private Boolean isFragile;
    private Double deliveryCost;
    private Double storageCost;
    private String shelfNumber;
    private Boolean homeDelivery;
    private Boolean hasCalled;
    private Boolean pickedUp;

    private String paymentMethod;
    private String shelfName;
    private Boolean inboudPayed;
    private String shelfId;

    private ParcelTypes typeofParcel;

    // For online
    private Double ItemCost;
    private Boolean isItemOwnerPaid;

    // For pickup
    private String pickupAddress;
    private String pickupContactName;
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number format")
    private String pickupContactPhoneNumber;
    private String pickupInstructions;
    private String deliveryAddress;
    private String deliveryContactName;
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number format")
    private String deliveryContactPhoneNumber;
    private String specialInstructions;
}
