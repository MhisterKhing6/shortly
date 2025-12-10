package shortly.mandmcorp.dev.shortly.service.parcel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import shortly.mandmcorp.dev.shortly.dto.request.ParcelRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.response.ParcelResponse;

public interface ParcelServiceInterface {
    ParcelResponse addParcel(ParcelRequest parcelRequest);
    ParcelResponse updateParcel(String parcelId, ParcelUpdateRequest updateRequest);
    Page<ParcelResponse> searchParcels(Boolean isPOD, Boolean isDelivered, Boolean isParcelAssigned, 
                                      String officeId, String driverId, String hasCalled, Pageable pageable, boolean isFrontDesk);
}
