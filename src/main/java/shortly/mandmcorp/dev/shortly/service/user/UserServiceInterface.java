package shortly.mandmcorp.dev.shortly.service.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import shortly.mandmcorp.dev.shortly.dto.request.ForgetPasswordRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ResetPasswordRequest;
import shortly.mandmcorp.dev.shortly.dto.request.UserLoginRequestDto;
import shortly.mandmcorp.dev.shortly.dto.request.UserRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.response.UserLoginResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserRegistrationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.model.User;



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


    /**
     * Initiates a password reset process for a user who has forgotten their password.
     * Validates the user exists, generates a secure OTP, and sends it via SMS notification.
     * The OTP can be used to verify identity before allowing password reset.
     * 
     * @param passwordRequest the password reset request containing user's phone number
     * @return UserResponse with reset status and user information (without sensitive data)
     * @throws UserAlreadyExistsException if user with the provided phone number does not exist
     * @throws RuntimeException if SMS notification fails to send
     */
    public UserResponse requestPasswordReset(ForgetPasswordRequest passwordRequest);

    /**
     * Resets user password after OTP verification.
     * Validates the OTP code, checks expiration, and updates user password.
     * 
     * @param restPasswordRequest the reset request containing OTP and new password
     * @return UserResponse with reset status and updated user information
     * @throws UserAlreadyExistsException if user does not exist
     * @throws IllegalArgumentException if OTP is invalid or expired
     */
    public UserResponse resetPassword(ResetPasswordRequest restPasswordRequest);


    public UserResponse deleteUser(String UserId);

    public UserResponse chageUserAvailabiltyStatus(String userId, String status);

    Page<User> getAllUsers(Pageable pageable);

}
