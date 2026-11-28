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

    /** Optional vendor-supplied barcode. Used only if non-blank and unique; otherwise auto-generated. */
    private String barCode;

    private double parcelWeight;

    private int numberOfItems;

    @NotBlank(message = "Destination station ID is required")
    private String destinationStationId;

    private double deliveryFee;

    private double itemCost;

    private int itemQuantity;

    private boolean isPOD = false;

    private java.util.List<String> images;

    // Optional delivery pin (gives partner parcels a map pin and a distance-based price)
    private Double receiverLatitude;
    private Double receiverLongitude;
    private String receiverLocationName;
    private String receiverPlaceId;
    private shortly.mandmcorp.dev.shortly.enums.LocationSource receiverLocationSource;

    /**
     * The POD flag is "POD" in JSON under Jackson 3 (Lombok's setPOD), while the API docs said "pod"
     * and older clients sent "isPOD". Accept all three so a POD parcel is never saved as non-POD.
     */
    @com.fasterxml.jackson.annotation.JsonProperty("POD")
    @com.fasterxml.jackson.annotation.JsonAlias({ "pod", "isPOD" })
    public void setPOD(boolean value) {
        this.isPOD = value;
    }
}
