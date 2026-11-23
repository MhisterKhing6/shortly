package shortly.mandmcorp.dev.shortly.dto.response;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Server-side delivery check for one rider over one period (Parts D2 and E3). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryCheckResponse {
    private String riderId;
    private String riderName;
    private Summary summary;
    @Builder.Default
    private List<Delivery> deliveries = new ArrayList<>();
    private OffTask offTask;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private int deliveries;
        private int reached;
        private int notReached;
        private int notFinished;
        private int noPin;
        private int noGps;
        private int markedAway;
        private double distanceKm;
        private long movingMinutes;
        private long stoppedMinutes;
    }

    public enum Result { REACHED, NOT_REACHED, NOT_FINISHED, NO_PIN, NO_GPS }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Delivery {
        private String assignmentId;
        private String parcelId;
        private String receiverName;
        private String receiverLocationName;
        private Double receiverLatitude;
        private Double receiverLongitude;
        private String status;
        private Long completedAt;
        private Result result;
        private Long arrivedAt;
        private Long leftAt;
        private Long minutesThere;
        private Long closestDistanceM;
        private Long closestAt;
        private Long markedDistanceFromDropoffM;
        private Boolean markedAway;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OffTask {
        private boolean judged;
        private String reason;
        private double directKm;
        private double distanceKm;
        private long offRouteMinutes;
        private double offRouteKm;
        @Builder.Default
        private List<Detour> detours = new ArrayList<>();
        @Builder.Default
        private List<OffTaskStop> offTaskStops = new ArrayList<>();
        private long offTaskStopMinutes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Detour {
        private long start;
        private long end;
        private String fromLabel;
        private String toLabel;
        private long maxOffRouteM;
        private double kmRidden;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OffTaskStop {
        private long start;
        private long end;
        private double lat;
        private double lng;
        private long minutes;
    }
}
