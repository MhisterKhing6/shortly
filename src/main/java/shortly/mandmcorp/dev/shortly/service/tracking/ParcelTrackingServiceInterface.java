package shortly.mandmcorp.dev.shortly.service.tracking;

import shortly.mandmcorp.dev.shortly.dto.request.TrackingAssignDriverRequest;
import shortly.mandmcorp.dev.shortly.dto.request.TrackingStatusUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.response.tracking.PublicTrackingResponse;
import shortly.mandmcorp.dev.shortly.model.Parcel;

public interface ParcelTrackingServiceInterface {
    PublicTrackingResponse getPublicTracking(String parcelId);

    Object getRoleAwareTracking(String parcelId);

    Parcel updateTrackingStatus(String parcelId, TrackingStatusUpdateRequest request);

    Parcel assignDriverToParcel(String parcelId, TrackingAssignDriverRequest request);
}
