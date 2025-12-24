package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

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
}
