package shortly.mandmcorp.dev.shortly.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RiderTrackResponse {
    private String riderId;
    private String riderName;
    private String deviceImei;
    private List<TrackPoint> trackPoints;

    @Data
    @Builder
    public static class TrackPoint {
        private Double lat;
        private Double lng;
        private Double speed;
        private String gpsTime;
        private Double direction;
        private Double altitude;
        private Integer posType;
    }
}
