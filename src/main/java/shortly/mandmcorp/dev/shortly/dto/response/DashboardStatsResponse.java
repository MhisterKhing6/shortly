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
public class DashboardStatsResponse {

    private double totalRevenue;
    private long totalParcels;
    private double successRate;
    private long activeRiders;

    private double collected;
    private double outstanding;
    private double driverPaymentsOwed;

    private List<DailyRevenueTrend> revenueVsCollected;
    private List<DailyDeliveryTrend> deliveredVsFailed;

    private ParcelPipeline parcelPipeline;
    private List<StationStats> stationSnapshot;
    private List<TopRider> topRiders;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyRevenueTrend {
        private String date;
        private double revenue;
        private double collected;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyDeliveryTrend {
        private String date;
        private long delivered;
        private long failed;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ParcelPipeline {
        private long registered;
        private long contacted;
        private long readyForDelivery;
        private long assigned;
        private long outForDelivery;
        private long delivered;
        private long failed;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StationStats {
        private String officeId;
        private String stationName;
        private long parcels;
        private double revenue;
        private double successRate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TopRider {
        private String riderId;
        private String riderName;
        private long deliveries;
        private double revenue;
    }
}
