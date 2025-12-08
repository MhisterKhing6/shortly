package shortly.mandmcorp.dev.shortly.dto.request;

import lombok.Data;

@Data
public class OfficeUpdateRequest {
    private String name;
    private String address;
    private String managerId;
    private String locationId;
}