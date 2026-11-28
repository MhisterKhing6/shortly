package shortly.mandmcorp.dev.shortly.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import shortly.mandmcorp.dev.shortly.service.pricing.DeliveryPricingRules;

/**
 * A delivery price quote (staff intake or customer booking). Customer quotes are valid 30 minutes;
 * all are kept 30 days for the pricing review (quotes shown vs booked).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "delivery_quotes")
public class DeliveryQuote {
    @Id
    private String id;
    /** STAFF or CUSTOMER. */
    private String kind;
    private String companyId;
    private String officeId;
    private String phoneNumber;
    @Builder.Default
    private List<String> parcelIds = new ArrayList<>();
    private double latitude;
    private double longitude;
    private Double weightKg;
    private boolean fragile;
    private double distanceKm;
    private String distanceSource;
    private String zone;
    private String bucketKey;
    private String bucketLabel;
    private int tableVersion;
    @Builder.Default
    private List<DeliveryPricingRules.Line> lines = new ArrayList<>();
    private double deliveryFee;
    private double parcelCharges;
    private double total;
    /** Per parcel: delivery share and charges still owed, for recording the payment. */
    @Builder.Default
    private List<ParcelShare> shares = new ArrayList<>();
    private long createdAt;
    private long validUntil;
    /** TTL: removed 30 days after creation. */
    private Date expiresAt;
    private String bookedRequestId;
    private String createdById;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParcelShare {
        private String parcelId;
        private double deliveryFee;
        private double charges;
    }
}
