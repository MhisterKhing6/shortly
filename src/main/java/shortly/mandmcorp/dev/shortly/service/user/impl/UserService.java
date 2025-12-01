package shortly.mandmcorp.dev.shortly.service.user.impl;


import java.util.ArrayList;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import shortly.mandmcorp.dev.shortly.dto.request.UserLoginRequestDto;
import shortly.mandmcorp.dev.shortly.dto.request.UserRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.response.UserLoginResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserRegistrationResponse;
import shortly.mandmcorp.dev.shortly.exceptions.UserAlreadyExistsException;
import shortly.mandmcorp.dev.shortly.exceptions.WrongCredentialsException;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.UserRepository;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationInterface;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationRequestTemplate;
import shortly.mandmcorp.dev.shortly.service.user.UserServiceInterface;
import shortly.mandmcorp.dev.shortly.utils.NotificationUtil;
import shortly.mandmcorp.dev.shortly.utils.OtpUtil;
import shortly.mandmcorp.dev.shortly.utils.UserMapper;

@Service
public class UserService implements UserServiceInterface {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final NotificationInterface notification;
    private final PasswordEncoder passwordEncoder;  

    public UserService(UserRepository userRepository, UserMapper userMapper, @Qualifier("smsNotification") NotificationInterface smsNotification, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.notification = smsNotification;
        this.passwordEncoder = passwordEncoder;
    }   

    @Override
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
        return userMapper.toUserLoginResponse(userEntity, loginDetails);   
    }

}
