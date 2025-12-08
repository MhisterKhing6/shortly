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
        return office;
    }
    
    public OfficeResponse toResponse(Office office) {
        OfficeResponse response = new OfficeResponse();
        response.setId(office.getId());
        response.setName(office.getName());
        response.setCode(office.getCode());
        response.setAddress(office.getAddress());
        response.setCreatedAt(office.getCreatedAt());
        if(office.getLocation() != null) {
            response.setLocationName(office.getLocation().getName());
        }
        if(office.getManager() != null) {
            response.setManagerName(office.getManager().getName());
        }
        return response;
    }
}