package shortly.mandmcorp.dev.shortly.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueDashboardResponse {

    private double totalRevenue;
    private double collected;
    private double outstanding;
    private double collectionRate;
    private double avgPerParcel;
    private double driverPayments;

    private List<DailyRevenueTrend> revenueVsCollectedTrend;
    private List<StationRevenue> revenueByStation;
    private List<DailyPaymentMethodTrend> paymentMethodTrend;
    private RevenueByType revenueByType;
    private List<PaymentMethodTotal> paymentMethods;
    private List<DayOfWeekRevenue> revenueByDayOfWeek;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyRevenueTrend {
        private String date;
        private double revenue;
        private double collected;
        private double outstanding;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StationRevenue {
        private String officeId;
        private String stationName;
        private long parcels;
        private double revenue;
        private double collected;
        private double outstanding;
        private double collectionRate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyPaymentMethodTrend {
        private String date;
        private double cash;
        private double momo;
        private double other;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RevenueByType {
        private double pod;
        private double nonPod;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PaymentMethodTotal {
        private String method;
        private double amount;
        private long count;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DayOfWeekRevenue {
        private String day;
        private double revenue;
    }
}
