package shortly.mandmcorp.dev.shortly.service.user.impl;


import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.config.FrontEndServerConfig;
import shortly.mandmcorp.dev.shortly.config.security.JWTConfig;
import shortly.mandmcorp.dev.shortly.dto.request.ForgetPasswordRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ResetPasswordRequest;
import shortly.mandmcorp.dev.shortly.dto.request.RiderStatusUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.UserLoginRequestDto;
import shortly.mandmcorp.dev.shortly.dto.request.UserRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.request.UserUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.response.UserLoginResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserRegistrationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.enums.UserRole;
import shortly.mandmcorp.dev.shortly.enums.UserStatusEnum;
import shortly.mandmcorp.dev.shortly.exceptions.EntityAlreadyExist;
import shortly.mandmcorp.dev.shortly.exceptions.EntityNotFound;
import shortly.mandmcorp.dev.shortly.exceptions.WrongCredentialsException;
import shortly.mandmcorp.dev.shortly.model.RiderStatusModel;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.model.VerificationToken;
import shortly.mandmcorp.dev.shortly.repository.RiderStatusRepository;
import shortly.mandmcorp.dev.shortly.repository.UserRepository;
import shortly.mandmcorp.dev.shortly.repository.VerificationTokenRepository;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationInterface;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationRequestTemplate;
import shortly.mandmcorp.dev.shortly.service.user.UserServiceInterface;
import shortly.mandmcorp.dev.shortly.utils.NotificationUtil;
import shortly.mandmcorp.dev.shortly.utils.OtpUtil;
import shortly.mandmcorp.dev.shortly.utils.UserMapper;

/**
 * Service implementation for user management operations.
 * Handles user registration, authentication, password reset, and user administration.
 * 
 * @author Shortly Team
 * @version 1.0
 * @since 1.0
 */
@Service
@Slf4j
public class UserService implements UserServiceInterface {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final NotificationInterface notification;
    private final PasswordEncoder passwordEncoder;
    private final JWTConfig jwt;
    private final VerificationTokenRepository verificationTokenRepository;
    private final FrontEndServerConfig frontendConfig;
    private final RiderStatusRepository riderStatusRepository;


    public UserService(FrontEndServerConfig frontend,UserRepository userRepository, UserMapper userMapper, @Qualifier("smsNotification") NotificationInterface smsNotification, PasswordEncoder passwordEncoder, JWTConfig jwtConfig, VerificationTokenRepository verificationTokenRepository, RiderStatusRepository riderStatusRepository) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.notification = smsNotification;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwtConfig;
        this.verificationTokenRepository = verificationTokenRepository;
        this.frontendConfig = frontend;
        this.riderStatusRepository = riderStatusRepository;
    }   

    /**
     * Registers a new user with auto-generated password.
     * Sends login credentials via SMS.
     * 
     * @param userRequestDetails user registration details
     * @return UserRegistrationResponse with user info
     * @throws EntityAlreadyExist if user already exists
     */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public UserRegistrationResponse register(UserRegistrationRequest userRequestDetails) {
        User registeredUser = userRepository.findByPhoneNumber(userRequestDetails.getPhoneNumber());

        if(registeredUser != null) {
            throw new EntityAlreadyExist("User already registered");  
        } 

        String password = OtpUtil.generateUserPassword();
        userRequestDetails.setPassword(password);
        User newUser = userMapper.toEntity(userRequestDetails);
        userRepository.save(newUser);
        
        // Create rider status if user is a rider
        if(newUser.getRole() == UserRole.RIDER) {
            RiderStatusModel riderStatus = new RiderStatusModel();
            riderStatus.setRider(newUser);
            riderStatus.setRiderStatus(shortly.mandmcorp.dev.shortly.enums.RiderStatus.OFFLINE);
            riderStatusRepository.save(riderStatus);
        }
        
        String message = NotificationUtil.loginCredentials(password, newUser.getPhoneNumber(), newUser.getName(), newUser.getRole().name());
        NotificationRequestTemplate loginCredentails =  NotificationRequestTemplate.builder().body(message).to(newUser.getPhoneNumber()).build();
        notification.send(loginCredentails);
        return userMapper.toUserRegistrationResponse(newUser);
    }   
    

    /**
     * Authenticates user and generates JWT token.
     * 
     * @param loginDetails phone number and password
     * @return UserLoginResponse with JWT token
     * @throws WrongCredentialsException if credentials are invalid
     */
    @Override
    public UserLoginResponse login(UserLoginRequestDto loginDetails) {
        User userEntity = userRepository.findByPhoneNumber(loginDetails.getPhoneNumber());
        if(userEntity == null) {
            throw new WrongCredentialsException ("phone number or password incorrect");
        }
        boolean isPasswordCorrect = passwordEncoder.matches(loginDetails.getPassword(), userEntity.getPasswordHash());
        if(!isPasswordCorrect) {
            throw new WrongCredentialsException("phone number or password incorrect");
        }
        String token = jwt.generateAccessToken(userEntity);
        return userMapper.toUserLoginResponse(userEntity, token);   
    }

    /**
     * Initiates password reset by generating verification token and sending SMS.
     * 
     * @param fr password reset request with phone number
     * @return UserResponse with success message
     * @throws WrongCredentialsException if user not found
     */
    @Override
    public UserResponse requestPasswordReset(ForgetPasswordRequest fr) {
        User user = userRepository.findByPhoneNumber(fr.getPhoneNumber());
        if(user == null) {
            throw new WrongCredentialsException("User not found");
        }
        String otp = OtpUtil.generateOtp();

        VerificationToken token = new VerificationToken();
        token.setUserId(user);
        token.setCreatedAt(LocalDateTime.now());
        token.setCode(otp);
        verificationTokenRepository.save(token);
        
        String otpMessage = NotificationUtil.generateOtpMessage(otp) ;
        NotificationRequestTemplate otpRequest = NotificationRequestTemplate.builder().body(otpMessage).to(user.getPhoneNumber()).build();
        notification.send(otpRequest);
        UserResponse userResponse = new UserResponse("Otp sent kindly check sms", token.getId());
        return userResponse;
    }

    /**
     * Resets user password using verification token.
     * Token expires after 5 minutes.
     * 
     * @param fr reset request with token and new password
     * @return UserResponse with success message
     * @throws WrongCredentialsException if token invalid or expired
     */
    @Override
    public UserResponse resetPassword(ResetPasswordRequest fr) {
        String tokenId = fr.getVerificationId();
        log.info("Resetting password for verification ID: {}", tokenId);
        
        VerificationToken token = verificationTokenRepository.findById(tokenId)
            .orElseThrow(() -> {
                log.error("Token not found in database: {}", tokenId);
                return new WrongCredentialsException("Invalid token");
            });
        if(token.getCode() != fr.getVerificationCode()) {
            throw new WrongCredentialsException("Invalid code");
        }
        log.info("Token found successfully for ID: {}", tokenId);
        
        if(token.getCreatedAt().plusMinutes(5).isBefore(LocalDateTime.now())) {
            throw new WrongCredentialsException("Token expired");
        }
        User user = token.getUserId();
        user.setPasswordHash(passwordEncoder.encode(fr.getNewPassword()));
        userRepository.save(user);
        verificationTokenRepository.delete(token);
        return new UserResponse("Password reset successful", user.getPhoneNumber());
    }
    

    /**
     * Deletes a user from the system.
     * 
     * @param userId user ID to delete
     * @return UserResponse with success message
     * @throws EntityNotFound if user not found
     */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse deleteUser(String userId) {
        log.error("Attempting to delete user with ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFound("User not found"));
        userRepository.delete(user);
        return new UserResponse("User deleted successfully", user.getPhoneNumber());
    }


    /**
     * Changes user availability status (ACTIVE/INACTIVE).
     * 
     * @param userId user ID
     * @param status new status (ACTIVE or INACTIVE)
     * @return UserResponse with success message
     * @throws EntityNotFound if user not found
     */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse chageUserAvailabiltyStatus(String userId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFound("User not found"));
        try {
            UserStatusEnum newStatus = UserStatusEnum.valueOf(status.toUpperCase());
            user.setStatus(newStatus);
            userRepository.save(user);
        return new UserResponse("User status changed successfully", user.getPhoneNumber());
        } catch (Exception e) {
            return UserResponse.builder()
                    .message("Invalid status value, available  are ACTIVE, INACTIVE")
                    .id(user.getPhoneNumber())
                    .build();
        }
        
    }

    /**
     * Retrieves all users with pagination.
     * 
     * @param pageable pagination parameters (page, size, sort)
     * @return Page of UserResponse
     */
    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    /**
     * Updates authenticated user's profile information.
     * 
     * @param updateRequest profile update data
     * @return UserResponse with success message
     * @throws WrongCredentialsException if user not authenticated
     * @throws EntityAlreadyExist if phone number already exists
     */
    @Override
    public UserResponse updateProfile(UserUpdateRequest updateRequest) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        
        User user = (User) auth.getPrincipal();
        User currentUser = userRepository.findById(user.getUserId())
            .orElseThrow(() -> new EntityNotFound("User not found"));
        
        if(updateRequest.getPhoneNumber() != null && !updateRequest.getPhoneNumber().equals(currentUser.getPhoneNumber())) {
            User existingUser = userRepository.findByPhoneNumber(updateRequest.getPhoneNumber());
            if(existingUser != null) {
                throw new EntityAlreadyExist("Phone number already exists");
            }
            currentUser.setPhoneNumber(updateRequest.getPhoneNumber());
        }
        
        if(updateRequest.getName() != null) currentUser.setName(updateRequest.getName());
        if(updateRequest.getEmail() != null) currentUser.setEmail(updateRequest.getEmail());
        
        userRepository.save(currentUser);
        return new UserResponse("Profile updated successfully", currentUser.getPhoneNumber());
    }

    /**
     * Updates rider status for authenticated rider.
     * 
     * @param statusRequest status update request
     * @return UserResponse with success message
     * @throws WrongCredentialsException if user not authenticated or not a rider
     */
    @Override
    public UserResponse updateRiderStatus(RiderStatusUpdateRequest statusRequest) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        
        User user = (User) auth.getPrincipal();
        if(user.getRole() != UserRole.RIDER) {
            throw new WrongCredentialsException("Only riders can update status");
        }
        
        RiderStatusModel riderStatus = riderStatusRepository.findByRider(user);
        if(riderStatus == null) {
            riderStatus = new RiderStatusModel();
            riderStatus.setRider(user);
        }
        
        riderStatus.setRiderStatus(statusRequest.getRiderStatus());
        riderStatusRepository.save(riderStatus);
        
        return new UserResponse("Rider status updated successfully", user.getPhoneNumber());
    }

}

