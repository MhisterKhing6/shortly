package shortly.mandmcorp.dev.shortly.dto.request;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.CounterPaymentMethod;
import shortly.mandmcorp.dev.shortly.enums.DriverAdjustmentType;
import shortly.mandmcorp.dev.shortly.enums.DriverStatus;
import shortly.mandmcorp.dev.shortly.enums.MomoNetwork;

/** Request bodies for the driver payments endpoints (Part M). */
public final class DriverPaymentRequests {

    private DriverPaymentRequests() {}

    @Data
    public static class DriverRequest {
        private String name;
        private List<String> phoneNumbers;
        private String momoNumber;
        private MomoNetwork momoNetwork;
        private List<String> vehicles;
        private String company;
        private DriverStatus status;
        private List<String> officeIds;
    }

    @Data
    public static class TripRequest {
        private String driverId;
        private String vehicleNumber;
        private String origin;
        private Integer declaredCount;
        private String note;
    }

    @Data
    public static class ReasonRequest {
        private String reason;
    }

    @Data
    public static class AdjustmentRequest {
        /** ADVANCE (cash given now), DEDUCTION or BONUS (applied at the next payout). */
        private DriverAdjustmentType type;
        private Double amount;
        private String reason;
        private String parcelId;
        /** For an ADVANCE: CASH (default, from the counter drawer) or MOMO. */
        private CounterPaymentMethod paymentMethod;
    }

    @Data
    public static class PayoutRequest {
        private String driverId;
        private List<String> lineIds = new ArrayList<>();
        private List<PayoutAdjustment> adjustments = new ArrayList<>();
        private CounterPaymentMethod paymentMethod;
        private String momoNumber;
        private String momoReference;
        private String note;
    }

    @Data
    public static class PayoutAdjustment {
        /** DEDUCTION, BONUS or ADVANCE_REPAID. */
        private DriverAdjustmentType type;
        private Double amount;
        private String reason;
        private String parcelId;
        /** An open DriverAdjustment this settles (required to recover a recorded advance). */
        private String adjustmentId;
    }

    @Data
    public static class ConfirmRequest {
        private String code;
    }
}
