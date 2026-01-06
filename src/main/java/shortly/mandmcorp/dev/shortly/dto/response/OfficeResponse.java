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
    private Long createdAt;
    private Long updatedAt;
}
