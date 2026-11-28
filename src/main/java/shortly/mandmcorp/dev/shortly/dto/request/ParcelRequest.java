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

    // Delivery location (sent only for home delivery when a pin was chosen)
    private Double receiverLatitude;
    private Double receiverLongitude;
    @jakarta.validation.constraints.Size(max = 200, message = "receiverLocationName must be at most 200 characters")
    private String receiverLocationName;
    @jakarta.validation.constraints.Size(max = 300, message = "receiverPlaceId must be at most 300 characters")
    private String receiverPlaceId;
    private shortly.mandmcorp.dev.shortly.enums.LocationSource receiverLocationSource;

    // Pickup pin (pickup requests)
    private Double pickupLatitude;
    private Double pickupLongitude;
    @jakarta.validation.constraints.Size(max = 200, message = "pickupLocationName must be at most 200 characters")
    private String pickupLocationName;
    @jakarta.validation.constraints.Size(max = 300, message = "pickupPlaceId must be at most 300 characters")
    private String pickupPlaceId;
    private shortly.mandmcorp.dev.shortly.enums.LocationSource pickupLocationSource;
    private String alternativePhoneNumber;

    private String parcelDescription;

    private double parcelWeight;

    /** Optional user-supplied barcode. Used only if non-blank and unique; otherwise auto-generated. */
    private String barCode;

    private String driverName;


    private String driverPhoneNumber;

    private boolean isPOD = false;
    private boolean isDelivered = false;
    private boolean isParcelAssigned = false;
    private double inboundCost;
    private boolean homeDelivery;

    /** Null means "not sent": pickup requests then fall back to the station's pickup charge. */
    private Double pickUpCost;
    
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
    private boolean parcelTransfer; // For transfer
    // For online
    private double itemCost;
    private boolean isItemOwnerPaid = false;
    private String fromOfficeId;
    private String toOfficeId;
    private boolean hasArrivedAtOffice = false; 
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
    //for calllcenter
    private boolean hasCallCenterSpokenToClient = false;

    private java.util.List<String> images;

    /** Saved driver record (driver payments). When absent, the driver is found or created from driverPhoneNumber. */
    private String driverId;
    /** Trip opened with POST /driver-trips. When absent, the driver's open trip for this vehicle and day is used. */
    private String tripId;

    // Delivery pricing (home deliveries with a pin). The server re-quotes and stores its own figures.
    private Double distanceKm;
    private String distanceSource;
    private Double suggestedDeliveryCost;
    private Integer pricingTableVersion;
    /** Required when deliveryCost differs from the suggested price. OTHER: explain in specialInstructions. */
    private shortly.mandmcorp.dev.shortly.enums.PriceOverrideReason priceOverrideReason;
}
