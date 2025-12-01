package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserLoginResponse {
    private String userId;
    private String token;
    private String phoneNumber;
    private String name;
    private String role;
    
}
