package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "parcels")
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
    private String driverId;
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
    private String inboudPayed;
    private String shelfId;
    private boolean homeDelivery;


    
    

}
