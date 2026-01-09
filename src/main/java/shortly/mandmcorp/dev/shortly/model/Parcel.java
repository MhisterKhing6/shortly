package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "parcels")
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
    private double pickUpCost;
    private boolean isFragile;
    private double deliveryCost;
    private double storageCost;
    private boolean  hasCalled;

    @Indexed
    private String driverId;

    @Indexed
    private String officeId;
    private String driverName;
    private String driverPhoneNumber;
    private String vehicleNumber;
    private String senderName;
    private String senderPhoneNumber;
    private String receiverName;
    private String receiverAddress;
    private String recieverPhoneNumber;
    private String shelfName;
    private boolean inboudPayed;
    private String shelfId;
    private boolean homeDelivery;
    private int cancelationCount = 0;

    @CreatedDate
    private Long createdAt;

    @LastModifiedDate
    private Long updatedAt;

}
