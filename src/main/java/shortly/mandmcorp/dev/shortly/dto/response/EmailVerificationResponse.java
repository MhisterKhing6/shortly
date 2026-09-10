package shortly.mandmcorp.dev.shortly.dto.response;

import shortly.mandmcorp.dev.shortly.enums.UserRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmailVerificationResponse {
    private String companyId;
    private String companyName;
    private String displayName;
    private boolean enabled;

    private String adminUserId;
    private String adminName;
    private String adminEmail;
    private String adminPhoneNumber;
    private UserRole adminRole;

    private String message;
}
