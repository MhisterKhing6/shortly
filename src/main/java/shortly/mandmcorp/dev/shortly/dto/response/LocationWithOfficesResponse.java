package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class LocationWithOfficesResponse {
    private String id;
    private String name;
    private String region;
    private String country;
    private List<OfficeResponse> offices;
}
