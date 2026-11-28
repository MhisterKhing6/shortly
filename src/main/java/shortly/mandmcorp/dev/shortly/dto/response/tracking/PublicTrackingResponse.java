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
    /** The station holding the parcel (or that will), for the customer page. */
    private shortly.mandmcorp.dev.shortly.dto.response.CustomerViews.OfficeView office;
    /** Customer view of the parcel (no staff-only fields). */
    private shortly.mandmcorp.dev.shortly.dto.response.CustomerViews.CustomerParcel parcel;
}
