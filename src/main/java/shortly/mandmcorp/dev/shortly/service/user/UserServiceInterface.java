package shortly.mandmcorp.dev.shortly.service.user;

import shortly.mandmcorp.dev.shortly.dto.request.UserLoginRequestDto;
import shortly.mandmcorp.dev.shortly.dto.request.UserRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.response.UserLoginResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserRegistrationResponse;

/**
 * Service interface for user management operations including registration and authentication.
 * Handles user lifecycle operations and integrates with notification services.
 * 
 * @author Shortly Team
 * @version 1.0
 * @since 1.0
 */
public interface UserServiceInterface {

    /**
     * Registers a new user in the system with auto-generated password.
     * Validates user data, checks for existing users, generates credentials,
     * and sends login details via SMS notification.
     * 
     * @param user the registration request containing user details
     * @return UserRegistrationResponse with registration status and user info
     * @throws UserAlreadyExistsException if user with phone number already exists
     * @throws IllegalArgumentException if user role is invalid
     */
    public UserRegistrationResponse register(UserRegistrationRequest user);

    /**
     * Authenticates user login credentials and returns session information.
     * Validates phone number and password against stored user data.
     * 
     * @param user the login request containing phone number and password
     * @return UserLoginResponse with authentication status and user session data
     * @throws UserAlreadyExistsException if user does not exist or password is incorrect
     */
    public UserLoginResponse login(UserLoginRequestDto user);
}
