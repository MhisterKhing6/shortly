package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VendorParcelRequest {

    @NotBlank(message = "Receiver name is required")
    private String receiverName;

    @NotBlank(message = "Receiver phone number is required")
    private String recieverPhoneNumber;

    private String recieverAlternativePhoneNumber;

    private String receiverAddress;

    private String parcelDescription;

    private double parcelWeight;

    private int numberOfItems;

    @NotBlank(message = "Destination station ID is required")
    private String destinationStationId;

    private double deliveryFee;

    private double itemCost;

    private int itemQuantity;

    private boolean isPOD = false;
}
