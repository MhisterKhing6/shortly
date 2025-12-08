package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.Data;

@Data
public class LocationResponse {
    private String id;
    private String name;
    private String region;
    private String country;
}