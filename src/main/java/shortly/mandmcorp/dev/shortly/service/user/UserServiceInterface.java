package shortly.mandmcorp.dev.shortly.service.user;

import shortly.mandmcorp.dev.shortly.dto.request.UserLoginRequestDto;
import shortly.mandmcorp.dev.shortly.dto.request.UserRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.response.UserLoginResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserRegistrationResponse;

public interface UserServiceInterface {

    public UserRegistrationResponse register(UserRegistrationRequest user);

    public UserLoginResponse login(UserLoginRequestDto user);
}
