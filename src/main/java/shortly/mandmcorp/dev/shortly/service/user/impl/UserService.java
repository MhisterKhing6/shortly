package shortly.mandmcorp.dev.shortly.service.user.impl;


import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import shortly.mandmcorp.dev.shortly.config.BackenServerConfig;
import shortly.mandmcorp.dev.shortly.config.security.JWTConfig;
import shortly.mandmcorp.dev.shortly.dto.request.ForgetPasswordRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ResetPasswordRequest;
import shortly.mandmcorp.dev.shortly.dto.request.UserLoginRequestDto;
import shortly.mandmcorp.dev.shortly.dto.request.UserRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.response.UserLoginResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserRegistrationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.exceptions.UserAlreadyExistsException;
import shortly.mandmcorp.dev.shortly.exceptions.WrongCredentialsException;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.model.VerificationToken;
import shortly.mandmcorp.dev.shortly.repository.UserRepository;
import shortly.mandmcorp.dev.shortly.repository.VerificationTokenRepository;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationInterface;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationRequestTemplate;
import shortly.mandmcorp.dev.shortly.service.user.UserServiceInterface;
import shortly.mandmcorp.dev.shortly.utils.MessageUtility;
import shortly.mandmcorp.dev.shortly.utils.NotificationUtil;
import shortly.mandmcorp.dev.shortly.utils.OtpUtil;
import shortly.mandmcorp.dev.shortly.utils.UserMapper;

@Service
public class UserService implements UserServiceInterface {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final NotificationInterface notification;
    private final PasswordEncoder passwordEncoder;
    private final JWTConfig jwt;
    private final VerificationTokenRepository verificationTokenRepository;
    private final BackenServerConfig backendConfig;


    public UserService(BackenServerConfig backend,UserRepository userRepository, UserMapper userMapper, @Qualifier("smsNotification") NotificationInterface smsNotification, PasswordEncoder passwordEncoder, JWTConfig jwtConfig, VerificationTokenRepository verificationTokenRepository) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.notification = smsNotification;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwtConfig;
        this.verificationTokenRepository = verificationTokenRepository;
        this.backendConfig = backend;
    }   

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public UserRegistrationResponse register(UserRegistrationRequest userRequestDetails) {
        User registeredUser = userRepository.findByPhoneNumber(userRequestDetails.getPhoneNumber());

        if(registeredUser != null) {
            throw new UserAlreadyExistsException("User already registered");  
        } 

        String password = OtpUtil.generateUserPassword();
        userRequestDetails.setPassword(password);
        User newUser = userMapper.toEntity(userRequestDetails);
        userRepository.save(newUser);
        String message = NotificationUtil.loginCredentials(password, newUser.getPhoneNumber(), newUser.getName(), newUser.getRole().name());
        NotificationRequestTemplate loginCredentails =  NotificationRequestTemplate.builder().body(message).to(newUser.getPhoneNumber()).build();
        notification.send(loginCredentails);
        return userMapper.toUserRegistrationResponse(newUser);
    }   
    

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

    @Override
    public UserResponse requestPasswordReset(ForgetPasswordRequest fr) {
        User user = userRepository.findByPhoneNumber(fr.getPhonNumber());
        if(user == null) {
            throw new WrongCredentialsException("User not found");
        }
        String otp = OtpUtil.generateOtp();

        VerificationToken token = new VerificationToken();
        token.setUserId(user);
        token.setCreatedAt(LocalDateTime.now());
        verificationTokenRepository.save(token);
        
        String otpMessage = MessageUtility.generateResetPasswordMessage(backendConfig.getBaseUrl(), token.getId(), user.getName());
        NotificationRequestTemplate otpRequest = NotificationRequestTemplate.builder().body(otpMessage).to(user.getPhoneNumber()).build();
        notification.send(otpRequest);
        UserResponse userResponse = new UserResponse("Otp sent kindly check sms", otp);
        return userResponse;
    }

    @Override
    public UserResponse resetPassword(ResetPasswordRequest fr) {
        VerificationToken token = verificationTokenRepository.findById(fr.getVerificaionId())
            .orElseThrow(() -> new WrongCredentialsException("Invalid token"));
        
        if(token.getCreatedAt().plusMinutes(5).isBefore(LocalDateTime.now())) {
            throw new WrongCredentialsException("Token expired");
        }
        User user = token.getUserId();
        user.setPasswordHash(passwordEncoder.encode(fr.getNewPassword()));
        userRepository.save(user);
        verificationTokenRepository.delete(token);
        return new UserResponse("Password reset successful", user.getPhoneNumber());
    }

}

