package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RiderLocationResponse {
    private String riderId;
    private String riderName;
    private String riderPhoneNumber;
    private String deviceImei;
    private Double lat;
    private Double lng;
    private Double speed;
    private String deviceStatus;
    private String accStatus;
    private String gpsTime;
    private Boolean located;
}
