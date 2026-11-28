package shortly.mandmcorp.dev.shortly.model;

import java.util.List;

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

    // Delivery location (drop-off pin). All nullable: older parcels, pickup parcels and
    // intake during a Google Maps outage have none.
    private Double receiverLatitude;
    private Double receiverLongitude;
    private String receiverLocationName;
    private String receiverPlaceId;
    private shortly.mandmcorp.dev.shortly.enums.LocationSource receiverLocationSource;

    // Pickup pin: where the rider collects the parcel from the sender (pickup requests).
    private Double pickupLatitude;
    private Double pickupLongitude;
    private String pickupLocationName;
    private String pickupPlaceId;
    private shortly.mandmcorp.dev.shortly.enums.LocationSource pickupLocationSource;

    /** When this parcel was marked DELIVERED or RETURNED (epoch ms). */
    private Long completedAt;

    // Rider's GPS position at the moment the parcel was marked DELIVERED/RETURNED.
    private Double markedLatitude;
    private Double markedLongitude;
    private String markedGpsTime;
    /** Distance from the marked position to the drop-off pin; null if either is unknown. */
    private Double markedDistanceFromDropoffM;
    private String senderName;
    private String senderPhoneNumber;
    private double parcelAmount;
    private double parcelWeight;
    private boolean payed;
    private boolean returned;
    private boolean delivered;
    private String paymentMethod;
    private double inboundCost;
    private double deliveryCost;

    private List<String> imageUrls;

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
