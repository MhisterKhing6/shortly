package shortly.mandmcorp.dev.shortly.service.office;

import java.util.List;

import shortly.mandmcorp.dev.shortly.dto.request.LocationRequest;
import shortly.mandmcorp.dev.shortly.dto.request.LocationUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.OfficeRequest;
import shortly.mandmcorp.dev.shortly.dto.request.OfficeUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ShelfRequest;
import shortly.mandmcorp.dev.shortly.dto.response.LocationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.LocationWithOfficesResponse;
import shortly.mandmcorp.dev.shortly.dto.response.OfficeResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.model.Shelf;

public interface OfficeServiceInterface {
    OfficeResponse addOffice(OfficeRequest officeRequest);
    LocationResponse addLocation(LocationRequest locationRequest);
    OfficeResponse updateOffice(String officeId, OfficeUpdateRequest updateRequest);
    LocationResponse updateLocation(String locationId, LocationUpdateRequest updateRequest);
    List<LocationWithOfficesResponse> getAllLocationsWithOffices(String locationName, String officeName);
    LocationWithOfficesResponse getLocationById(String locationId);
    UserResponse addShelf(ShelfRequest shelf );
    List<Shelf> getOfficeShelf(String officeId) ;


}