package shortly.mandmcorp.dev.shortly.dto.response.tracking;

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
public class PublicTrackingResponse {
    private String parcelId;
    private ParcelStatus parcelStatus;
    private ParcelTypes typeofParcel;
    private boolean isDelivered;
    private boolean isParcelAssigned;
    private String fromOfficeName;
    private String toOfficeName;
    private Long createdAt;
    private Long updatedAt;
}
