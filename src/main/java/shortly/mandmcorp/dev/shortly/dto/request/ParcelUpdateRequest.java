package shortly.mandmcorp.dev.shortly.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.LocationSource;
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
    private String fromOfficeId;
    private String toOfficeId;
    private Boolean hasArrivedAtOffice;

    // Rider
    private String riderId;

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

    // ---- Delivery location ----
    // Absent = leave as is; explicit null = clear. Jackson only calls a setter for properties
    // present in the body, so each setter records that the location was sent.
    private Double receiverLatitude;
    private Double receiverLongitude;
    @Size(max = 200, message = "receiverLocationName must be at most 200 characters")
    private String receiverLocationName;
    @Size(max = 300, message = "receiverPlaceId must be at most 300 characters")
    private String receiverPlaceId;
    private LocationSource receiverLocationSource;

    @JsonIgnore
    private boolean receiverLocationProvided;

    public void setReceiverLatitude(Double value) { this.receiverLatitude = value; this.receiverLocationProvided = true; }
    public void setReceiverLongitude(Double value) { this.receiverLongitude = value; this.receiverLocationProvided = true; }
    public void setReceiverLocationName(String value) { this.receiverLocationName = value; this.receiverLocationProvided = true; }
    public void setReceiverPlaceId(String value) { this.receiverPlaceId = value; this.receiverLocationProvided = true; }
    public void setReceiverLocationSource(LocationSource value) { this.receiverLocationSource = value; this.receiverLocationProvided = true; }

    // ---- Pickup pin: same absent/null rules as the delivery location ----
    private Double pickupLatitude;
    private Double pickupLongitude;
    @Size(max = 200, message = "pickupLocationName must be at most 200 characters")
    private String pickupLocationName;
    @Size(max = 300, message = "pickupPlaceId must be at most 300 characters")
    private String pickupPlaceId;
    private LocationSource pickupLocationSource;

    @JsonIgnore
    private boolean pickupLocationProvided;

    public void setPickupLatitude(Double value) { this.pickupLatitude = value; this.pickupLocationProvided = true; }
    public void setPickupLongitude(Double value) { this.pickupLongitude = value; this.pickupLocationProvided = true; }
    public void setPickupLocationName(String value) { this.pickupLocationName = value; this.pickupLocationProvided = true; }
    public void setPickupPlaceId(String value) { this.pickupPlaceId = value; this.pickupLocationProvided = true; }
    public void setPickupLocationSource(LocationSource value) { this.pickupLocationSource = value; this.pickupLocationProvided = true; }

    /** Accept "isPOD", "POD" and "pod" for the POD flag (see ParcelRequest.setPOD). */
    @com.fasterxml.jackson.annotation.JsonAlias({ "POD", "pod" })
    public void setIsPOD(Boolean value) {
        this.isPOD = value;
    }
}
