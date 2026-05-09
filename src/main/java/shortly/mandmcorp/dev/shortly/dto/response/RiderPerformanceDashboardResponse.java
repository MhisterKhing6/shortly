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
public class RiderPerformanceDashboardResponse {

    private List<DailyEarningsTrend> earningsTrend;
    private List<MonthlyEarningsSummary> monthlyEarnings;
    private List<RiderLeaderboardEntry> riderLeaderboard;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyEarningsTrend {
        private String date;
        private double revenue;
        private double collected;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MonthlyEarningsSummary {
        private String month;
        private long parcels;
        private double revenue;
        private double collected;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RiderLeaderboardEntry {
        private int rank;
        private String riderId;
        private String riderName;
        private long deliveries;
        private long failed;
        private double revenue;
        private double outstanding;
        private double rating;
        private double avgTimeHours;
    }
}
