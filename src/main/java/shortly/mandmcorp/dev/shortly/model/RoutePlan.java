package shortly.mandmcorp.dev.shortly.model;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Planned stop order for an assignment, computed by the frontend (Google Directions or a
 * straight-line estimate) and stored as-is. Nothing queries inside it; it is a guide for the
 * rider app and for off-task checks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutePlan {
    public static final int MAX_ENCODED_PATH_LENGTH = 50 * 1024;

    /** GOOGLE (road distances) or ESTIMATE (straight-line). */
    private String method;
    /** Parcel ids in visiting order. Only pinned parcels. */
    @Builder.Default
    private List<String> stopOrder = new ArrayList<>();
    private Double totalKm;
    private Double totalMinutes;
    @Builder.Default
    private List<Leg> legs = new ArrayList<>();
    /** Google encoded polyline, precision 5. */
    private String encodedPath;
    private Double startLatitude;
    private Double startLongitude;
    private boolean edited;
    private Long plannedAt;
    /** Set when a stop's pin changed after planning. */
    private boolean stale;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Leg {
        /** Parcel id, or START for the station. */
        private String from;
        /** Parcel id, or END for the station. */
        private String to;
        private Double km;
        private Double minutes;
    }
}
