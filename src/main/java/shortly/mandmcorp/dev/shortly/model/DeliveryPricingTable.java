package shortly.mandmcorp.dev.shortly.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A station's delivery price table. Every save is a new version (1, 2, 3...); old versions are
 * kept so old parcels can still be explained.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "delivery_pricing_tables")
public class DeliveryPricingTable {
    @Id
    private String id;
    private String companyId;
    private String officeId;
    private int version;

    /** BANDS or PER_KM. */
    private String mode;
    @Builder.Default
    private List<Band> bands = new ArrayList<>();
    /** BANDS: added per whole km past the last band. */
    private double perKmBeyond;
    /** PER_KM: max(minFee, baseFee + perKm × km). */
    private double baseFee;
    private double perKm;
    private double minFee;
    @Builder.Default
    private List<WeightBand> weightBands = new ArrayList<>();
    @Builder.Default
    private List<Zone> zones = new ArrayList<>();
    private double fragileFee;
    /** Round the total up to a multiple of this; 0 = don't round. */
    private double roundTo;
    /** Customer bookings: added for each parcel after the first in one trip. */
    private double extraParcelFee;

    private String note;
    private long updatedAt;
    private String updatedById;
    private String updatedByName;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Band {
        private double upToKm;
        private double price;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeightBand {
        private double overKg;
        private double surcharge;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Zone {
        private String name;
        private double lat;
        private double lng;
        private double radiusKm;
        private double price;
    }
}
