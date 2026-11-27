package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.Data;

@Data
public class OfficeResponse {
    private String id;
    private String name;
    private String code;
    private String address;
    private String phoneNumber;
    private String locationName;
    private String managerName;
    private Double latitude;
    private Double longitude;
    private Boolean pickupChargeEnabled;
    private Double pickupChargeAmount;
    private Long createdAt;
    private Long updatedAt;
    /** When driver fees become payable: ON_COLLECTION (default) or ON_ARRIVAL. */
    private shortly.mandmcorp.dev.shortly.enums.DriverPayPolicy driverPayPolicy;
}
