package shortly.mandmcorp.dev.shortly.utils;

import org.springframework.stereotype.Component;

import shortly.mandmcorp.dev.shortly.dto.request.OfficeRequest;
import shortly.mandmcorp.dev.shortly.dto.response.OfficeResponse;
import shortly.mandmcorp.dev.shortly.model.Office;

@Component
public class OfficeMapper {
    
    public Office toEntity(OfficeRequest request) {
        Office office = new Office();
        office.setName(request.getName());
        office.setAddress(request.getAddress());
        office.setPhoneNumber(request.getPhoneNumber());
        office.setLatitude(request.getLatitude());
        office.setLongitude(request.getLongitude());
        office.setPickupChargeEnabled(request.getPickupChargeEnabled());
        office.setPickupChargeAmount(request.getPickupChargeAmount());
        office.setDriverPayPolicy(request.getDriverPayPolicy());
        office.setOpeningHours(request.getOpeningHours());
        office.setCreatedAt(System.currentTimeMillis());
        office.setUpdatedAt(System.currentTimeMillis());
        return office;
    }
    
    public OfficeResponse toResponse(Office office) {
        OfficeResponse response = new OfficeResponse();
        response.setId(office.getId());
        response.setName(office.getName());
        response.setCode(office.getCode());
        response.setAddress(office.getAddress());
        response.setPhoneNumber(office.getPhoneNumber());
        response.setCreatedAt(office.getCreatedAt());
        response.setUpdatedAt(office.getUpdatedAt());
        response.setLatitude(office.getLatitude());
        response.setLongitude(office.getLongitude());
        response.setPickupChargeEnabled(office.getPickupChargeEnabled());
        response.setPickupChargeAmount(office.getPickupChargeAmount());
        response.setOpeningHours(office.getOpeningHours());
        response.setDriverPayPolicy(office.getDriverPayPolicy() != null ? office.getDriverPayPolicy()
                : shortly.mandmcorp.dev.shortly.enums.DriverPayPolicy.ON_COLLECTION);
        if(office.getLocation() != null) {
            response.setLocationName(office.getLocation().getName());
        }
        if(office.getManager() != null) {
            response.setManagerName(office.getManager().getName());
        }
        return response;
    }
}