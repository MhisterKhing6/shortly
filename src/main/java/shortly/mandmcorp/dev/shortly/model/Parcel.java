package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.CallCenterCallOutCome;
import shortly.mandmcorp.dev.shortly.enums.ParcelTypes;

@Data
@Document(collection = "parcelss")
@CompoundIndexes({
    @CompoundIndex(name = "office_delivered_idx", def = "{'officeId': 1, 'isDelivered': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "office_homedelivery_idx", def = "{'officeId': 1, 'homeDelivery': 1, 'isDelivered': 1}"),
    @CompoundIndex(name = "office_called_idx", def = "{'officeId': 1, 'hasCalled': 1}"),
    @CompoundIndex(name = "search_parcels_idx", def = "{'officeId': 1, 'isPOD': 1, 'isDelivered': 1, 'isParcelAssigned': 1, 'hasCalled': 1}")
})
public class Parcel {
    @Id
    private String parcelId;
    private String parcelDescription;
    private boolean isPOD;
    private boolean isDelivered;
    private boolean isParcelAssigned;
    private double inboundCost;
    private boolean isFragile;
    private double deliveryCost;
    private double storageCost;
    private boolean  hasCalled;
    private boolean pickedUp;

    @Indexed
    private String driverId;

    @Indexed
    private String officeId;
    private String paymentMethod;
    private String driverName;
    private String driverPhoneNumber;
    private String vehicleNumber;
    private String senderName;
    private String senderPhoneNumber;
    private String receiverName;
    private String receiverAddress;
    private String recieverPhoneNumber;
    private String alternativePhoneNumber;
    private String shelfName;
    private boolean inboudPayed;
    private String shelfId;
    private boolean homeDelivery;
    private int returnCount = 0;
    private String riderId;
    private RiderInfo riderInfo;

    private ParcelTypes typeofParcel = ParcelTypes.PARCEL;
    
    //for online
    private double ItemCost;
    private OfficeInfo from;
    private OfficeInfo to;
    private String fromOfficeId;
    private String toOfficeId;
    private boolean hasArrivedAtOffice = false;
    private boolean isItemOwnerPaid = false;
    //for transfer
    private boolean parcelTransfer = false;
    //For pickup
    private String pickupAddress;
    private String pickupContactName;
    private String pickupContactPhoneNumber;
    private String pickupInstructions;
    private String deliveryAddress;
    private String deliveryContactName;
    private String deliveryContactPhoneNumber;
    private String specialInstructions;
    private double pickUpCost;
    
    @CreatedDate
    private Long createdAt;

    @LastModifiedDate
    private Long updatedAt;

    //caller details
    private String callerName;
    private String callerPhoneNumber;
    private String notes; // Any additional notes about the call
    private boolean hasCallCenterSpokenToClient;
    private CallCenterCallOutCome callOutCome;
    private String callCenterRemark;
}
