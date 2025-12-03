package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;

import lombok.Data;

@Data
public class Parcel {
    @Id
    private String parcelId;

    private String senderName;
    private String senderPhoneNumber;
    private String receiverName;
    private String receiverAddress;
    private String recieverPhoneNumber;
    private String parcelDescription;
    private String driverName;
    private String driverPhoneNumber;
    private boolean isPOD;
    private boolean isDelivered;
    private boolean isParcelAssigned;
    private double inboundCost;
    private double pickUpCost;
    private boolean isFragile;
    private double deliveryCost;
    private double storageCost;

}
