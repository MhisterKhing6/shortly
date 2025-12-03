package shortly.mandmcorp.dev.shortly.dto.request;

import org.springframework.data.annotation.Id;

import lombok.Data;

@Data
public class ParcelEntry {
    @Id
    private Integer parcelId;
    private String senderName;
    private String senderPhoneNumber;
    private String receiverName;
    private String receiverAddress;
    private String recieverPhoneNumber;
    private String parcelDescription;
    private String driverName;
    private String driverPhoneNumber;
    private String isPOD;
    private String isDelivered;
    private String isParcelAssigned;
    private String inboundCost;
    private Double pickUpCost;
    private Boolean isFragile;
    private Double deliveryCost;
    private Double storageCost;

}
