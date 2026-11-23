package shortly.mandmcorp.dev.shortly.dto.request;

import lombok.Data;

@Data
public class AddAddressRequest {
    private String name;
    private double cost;
    private Double latitude;
    private Double longitude;
    private String locationName;
}
