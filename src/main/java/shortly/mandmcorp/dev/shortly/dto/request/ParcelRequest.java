package shortly.mandmcorp.dev.shortly.dto.request;


import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.ParcelTypes;

@Data
public class ParcelRequest {
    private String senderName;

    
    private String senderPhoneNumber;
    
    private String receiverName;
    private String receiverAddress;

    private String recieverPhoneNumber;
    
    private String parcelDescription;
    
    private String driverName;


    private String driverPhoneNumber;

    private boolean isPOD = false;
    private boolean isDelivered = false;
    private boolean isParcelAssigned = false;
    private double inboundCost;
    private boolean homeDelivery;

    private double pickUpCost;
    
    private boolean isFragile;
    private double deliveryCost;
    private double storageCost;

    private String shelfNumber;

    private boolean hasCalled = false;
    private boolean pickedUp = false;

    private String vehicleNumber;

    private String officeId;

    private String paymentMethod;
    private String shelfName;
    private boolean inboudPayed = false;
    private String shelfId;

    private ParcelTypes typeofParcel;

    // For online
    private double ItemCost;
    private boolean isItemOwnerPaid = false;

    // For pickup
    private String pickupAddress;
    private String pickupContactName;
    private String pickupContactPhoneNumber;
    private String pickupInstructions;
    private String deliveryAddress;
    private String deliveryContactName;
    private String deliveryContactPhoneNumber;
    private String specialInstructions;
    private String riderId;
}