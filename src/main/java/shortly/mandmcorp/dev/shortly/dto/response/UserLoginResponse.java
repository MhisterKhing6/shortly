package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.Data;

@Data
public class UserLoginResponse {
    private String userId;
    private String token;
    private String phoneNumber;
    
}
