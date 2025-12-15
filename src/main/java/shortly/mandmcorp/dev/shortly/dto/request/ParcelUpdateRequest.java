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
    private boolean isPOD;
    private boolean isDelivered;
    private boolean isParcelAssigned;
    private double inboundCost;
    private double pickUpCost;
    private boolean isFragile;
    private double deliveryCost;
    private double storageCost;
    private String shelfNumber;
}