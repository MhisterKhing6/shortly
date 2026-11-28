package shortly.mandmcorp.dev.shortly.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A delivery booked and paid by the receiver on the public page. FLAGGED = paid but couldn't be applied. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "customer_delivery_requests")
public class CustomerDeliveryRequest {
    @Id
    private String id;
    private String companyId;
    private String officeId;
    private String phoneNumber;
    @Builder.Default
    private List<String> parcelIds = new ArrayList<>();
    private double latitude;
    private double longitude;
    private String address;
    private String notes;
    private String scheduledDate;
    private String scheduledTime;
    private String quoteId;
    private double deliveryFee;
    private double parcelCharges;
    private double total;
    private double amountPaid;
    /** Unique: each Paystack reference can be used once. */
    private String paystackReference;
    /** Amount Paystack confirmed, in cedis. */
    private Double paystackAmount;
    /** PENDING (applied to the parcels) or FLAGGED (paid, not applied: refund or handle manually). */
    private String status;
    private String flagReason;
    private long createdAt;
}
