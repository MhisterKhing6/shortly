package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ParcelRequest {
    private String senderName;

    
    private String senderPhoneNumber;
    
    private String receiverName;
    private String receiverAddress;

    @NotBlank(message = "Receiver phone number is required")
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number format")
    private String recieverPhoneNumber;
    
    private String parcelDescription;
    
    @NotBlank(message = "Driver name is required")
    private String driverName;


    @NotBlank(message = "Driver phone number is required")
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number format")
    private String driverPhoneNumber;

    private boolean isPOD = false;
    private boolean isDelivered = false;
    private boolean isParcelAssigned = false;
    private double inboundCost;

    @NotNull(message = "Pick up cost is required")
    private double pickUpCost;
    
    private boolean isFragile;
    private double deliveryCost;
    private double storageCost;

    @NotBlank(message ="shelf number is required ")
    private String shelfNumber;

    private boolean hasCalled = false;

    @NotBlank(message = "Vehicle number is required")
    private String vehicleNumber;
    
    private String officeId;
}