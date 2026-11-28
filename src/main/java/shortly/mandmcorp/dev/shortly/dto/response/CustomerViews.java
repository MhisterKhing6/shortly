package shortly.mandmcorp.dev.shortly.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import shortly.mandmcorp.dev.shortly.enums.LocationSource;
import shortly.mandmcorp.dev.shortly.enums.ParcelStatus;
import shortly.mandmcorp.dev.shortly.enums.ParcelTypes;

/** Public customer-page shapes: only what a receiver may see. */
public final class CustomerViews {

    private CustomerViews() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OfficeView {
        private String id;
        private String name;
        private String address;
        private String phoneNumber;
        private Double latitude;
        private Double longitude;
        private String openingHours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiderView {
        private String name;
        private String phoneNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerParcel {
        private String parcelId;
        private String parcelDescription;
        private ParcelTypes typeofParcel;
        private int numberOfItems;
        private double parcelWeight;
        private boolean fragile;
        private String senderName;
        private String receiverName;
        private String receiverAddress;
        private Double receiverLatitude;
        private Double receiverLongitude;
        private String receiverLocationName;
        private String receiverPlaceId;
        private LocationSource receiverLocationSource;
        private String shelfName;
        private boolean hasArrivedAtOffice;
        private boolean parcelAssigned;
        private boolean homeDelivery;
        private boolean delivered;
        private boolean pickedUp;
        private ParcelStatus parcelStatus;
        private double inboundCost;
        private double storageCost;
        private double deliveryCost;
        private double pickUpCost;
        private boolean inboudPayed;
        private boolean pod;
        private double itemCost;
        /** Paid online when the customer booked delivery. */
        private Double deliveryPrepaidAmount;
        private boolean deliveryBooked;
        private String scheduledDeliveryDate;
        private String scheduledDeliveryTime;
        private Long createdAt;
        private Long updatedAt;
        /** Only while the parcel is out for delivery. */
        private RiderView riderInfo;
        /** The station holding it (or that will). */
        private OfficeView office;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkResponse {
        private String phoneNumber;
        private String receiverName;
        private List<CustomerParcel> parcels;
    }

    @Data
    public static class PhoneRequest {
        private String phoneNumber;
        private String code;
    }

    @Data
    public static class QuoteRequest {
        private String token;
        private String phoneNumber;
        private List<String> parcelIds;
        private Double latitude;
        private Double longitude;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuoteResponse {
        private double deliveryFee;
        private double parcelCharges;
        private double total;
        private String quoteId;
        private double distanceKm;
        private long validUntil;
    }

    @Data
    public static class BookingRequest {
        private String token;
        private String phoneNumber;
        private List<String> parcelIds;
        private Double latitude;
        private Double longitude;
        private String address;
        private String notes;
        private String scheduledDate;
        private String scheduledTime;
        private String quoteId;
        private Double amountPaid;
        private String paystackReference;
    }
}
