package shortly.mandmcorp.dev.shortly.dto.response;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import shortly.mandmcorp.dev.shortly.enums.DriverFeeState;
import shortly.mandmcorp.dev.shortly.model.Driver;
import shortly.mandmcorp.dev.shortly.model.DriverAdjustment;
import shortly.mandmcorp.dev.shortly.model.DriverPayout;

/** Response shapes for the driver payments endpoints (Part M). */
public final class DriverPaymentViews {

    private DriverPaymentViews() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Balances {
        private double readyAmount;
        private int readyCount;
        private double waitingAmount;
        private int waitingCount;
        private double onHoldAmount;
        private int onHoldCount;
        private double paidLast30Days;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceRow {
        private String driverId;
        private String name;
        private String phone;
        private List<String> vehicles;
        private String company;
        private double readyAmount;
        private int readyCount;
        private double waitingAmount;
        private double onHoldAmount;
        private double paidLast30Days;
        private Long lastTripAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeeLine {
        private String id;
        private String parcelId;
        private String receiverName;
        private double amount;
        private DriverFeeState state;
        private Long readyAt;
        private String readyReason;
        private String holdReason;
        private String shelfName;
        private String payoutId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Trip {
        /** null for parcels registered before trips existed. */
        private String tripId;
        private String vehicleNumber;
        private String origin;
        private Long arrivedAt;
        private String receivedByName;
        private Integer declaredCount;
        private int receivedCount;
        /** True when receivedCount differs from declaredCount. */
        private boolean flagged;
        private String note;
        private String driverId;
        private String driverName;
        private double feeTotal;
        private double readyAmount;
        private boolean fullyPaid;
        @Builder.Default
        private List<FeeLine> lines = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Statement {
        private Driver driver;
        private Balances balances;
        private List<Trip> trips;
        private List<DriverPayout> payouts;
        private List<DriverAdjustment> openAdjustments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriverPaymentsSummary {
        private double readyAmount;
        private int readyDrivers;
        private double waitingAmount;
        private int waitingParcels;
        private double paidThisWeekAmount;
        private int paidThisWeekCount;
        /** Share of this week's paid amount sent by MoMo, 0–100. */
        private double paidThisWeekMomoPercent;
        private double onHoldAmount;
        private int onHoldCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayoutTotals {
        private String officeId;
        private String officeName;
        private String driverId;
        private String driverName;
        private int payouts;
        private double gross;
        private double deductions;
        private double bonuses;
        private double net;
        private double cash;
        private double momo;
        private int unconfirmed;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayoutReport {
        private List<PayoutTotals> byStation;
        private List<PayoutTotals> byDriver;
    }
}
