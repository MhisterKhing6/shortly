package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.Builder;
import lombok.Data;
import shortly.mandmcorp.dev.shortly.model.Office;

@Data
@Builder
public class UserLoginResponse {
    private String userId;
    private String token;
    private String phoneNumber;
    private String name;
    private String role;
    private String officeId;
    
}
