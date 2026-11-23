package shortly.mandmcorp.dev.shortly.dto.request;

import lombok.Data;

@Data
public class OfficeUpdateRequest {
    private String name;
    private String address;
    private String managerId;
    private String locationId;
    private String code;
    private String phoneNumber;
    private Double latitude;
    private Double longitude;
    /** When present, pickupChargeAmount is applied with it (null amount when disabled). */
    private Boolean pickupChargeEnabled;
    private Double pickupChargeAmount;
}