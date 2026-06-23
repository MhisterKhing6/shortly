package shortly.mandmcorp.dev.shortly.dto.response.tracking;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import shortly.mandmcorp.dev.shortly.enums.CallCenterCallOutCome;
import shortly.mandmcorp.dev.shortly.enums.ParcelStatus;
import shortly.mandmcorp.dev.shortly.enums.ParcelTypes;
import shortly.mandmcorp.dev.shortly.model.OfficeInfo;
import shortly.mandmcorp.dev.shortly.model.RiderInfo;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTrackingResponse {
    private String parcelId;
    private String parcelDescription;
    private ParcelStatus parcelStatus;
    private ParcelTypes typeofParcel;
    private boolean isPOD;
    private boolean isDelivered;
    private boolean isParcelAssigned;
    private boolean isFragile;
    private boolean homeDelivery;
    private boolean pickedUp;
    private double inboundCost;
    private double deliveryCost;
    private double storageCost;
    private double pickUpCost;
    private double ItemCost;
    private String paymentMethod;
    private String senderName;
    private String senderPhoneNumber;
    private String receiverName;
    private String receiverAddress;
    private String recieverPhoneNumber;
    private String alternativePhoneNumber;
    private String driverId;
    private String driverName;
    private String driverPhoneNumber;
    private String vehicleNumber;
    private String officeId;
    private String shelfName;
    private String shelfId;
    private boolean inboudPayed;
    private String riderId;
    private RiderInfo riderInfo;
    private OfficeInfo from;
    private OfficeInfo to;
    private String fromOfficeId;
    private String toOfficeId;
    private boolean hasArrivedAtOffice;
    private boolean isItemOwnerPaid;
    private boolean parcelTransfer;
    private String pickupAddress;
    private String pickupContactName;
    private String pickupContactPhoneNumber;
    private String pickupInstructions;
    private String deliveryAddress;
    private String deliveryContactName;
    private String deliveryContactPhoneNumber;
    private String specialInstructions;
    private String vendorName;
    private String vendorId;
    private double vendorDeliveryFee;
    private int numberOfItems;
    private int itemQuantity;
    private double parcelWeight;
    private boolean isVendorPayed;
    private String callerName;
    private String callerPhoneNumber;
    private String notes;
    private boolean hasCallCenterSpokenToClient;
    private CallCenterCallOutCome callOutCome;
    private String callCenterRemark;
    private boolean hasCalled;
    private int returnCount;
    private List<String> imageUrls;
    private Long createdAt;
    private Long updatedAt;
}
