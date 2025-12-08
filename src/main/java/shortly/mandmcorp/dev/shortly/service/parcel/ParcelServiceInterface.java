package shortly.mandmcorp.dev.shortly.service.parcel;

import shortly.mandmcorp.dev.shortly.dto.request.ParcelRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.response.ParcelResponse;

public interface ParcelServiceInterface {
    ParcelResponse addParcel(ParcelRequest parcelRequest);
    ParcelResponse updateParcel(String parcelId, ParcelUpdateRequest updateRequest);
}
