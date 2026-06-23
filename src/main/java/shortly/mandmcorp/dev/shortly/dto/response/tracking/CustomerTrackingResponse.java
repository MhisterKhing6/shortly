package shortly.mandmcorp.dev.shortly.dto.response.tracking;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import shortly.mandmcorp.dev.shortly.enums.ParcelStatus;
import shortly.mandmcorp.dev.shortly.enums.ParcelTypes;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerTrackingResponse {
    private String parcelId;
    private String parcelDescription;
    private ParcelStatus parcelStatus;
    private ParcelTypes typeofParcel;
    private boolean isDelivered;
    private boolean isParcelAssigned;
    private String senderName;
    private String receiverName;
    private String fromOfficeName;
    private String toOfficeName;
    private Long createdAt;
    private Long updatedAt;
    private List<TrackingEvent> timeline;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingEvent {
        private String status;
        private String description;
        private Long timestamp;
    }
}
