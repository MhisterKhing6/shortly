package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.Builder;
import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.UserRole;

@Data
@Builder
public class UserRegistrationResponse {
    private String name;
    private String phoneNumber;
    private String email;
    private String userId;
    private UserRole role;
}
