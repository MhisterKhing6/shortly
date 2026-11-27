package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PickedUpRequest {
    @NotBlank(message = "Parcel ID is required")
    private String parcelId;
    private String whoPickedUpTelephoneNumber;
    private String whoPickedUpName;

    // ---- Counter payment (all optional; old clients send none of these) ----
    /** Pickup fee charged at the counter; saved to the parcel before amountDue is computed. */
    private Double pickUpCost;
    /** What the frontend computed; rejected (400) if it differs from the server's figure by > GHC 0.01. */
    private Double amountDue;
    /** What the customer handed over. Its presence turns on payment recording. */
    private Double amountPaid;
    private Double varianceAmount;
    private shortly.mandmcorp.dev.shortly.enums.CounterPaymentMethod paymentMethod;
    private String momoReference;
    private String varianceReason;
    private String varianceNote;
    private java.util.List<shortly.mandmcorp.dev.shortly.model.CounterCharge> charges;
}
