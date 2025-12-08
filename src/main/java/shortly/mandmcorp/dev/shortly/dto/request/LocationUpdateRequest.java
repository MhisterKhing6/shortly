package shortly.mandmcorp.dev.shortly.dto.request;

import lombok.Data;

@Data
public class LocationUpdateRequest {
    private String name;
    private String region;
    private String country;
}