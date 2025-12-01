package shortly.mandmcorp.dev.shortly.utils;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.dto.request.UserLoginRequestDto;
import shortly.mandmcorp.dev.shortly.dto.request.UserRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.response.UserLoginResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserRegistrationResponse;
import shortly.mandmcorp.dev.shortly.model.User;


@Component
@AllArgsConstructor
public class UserMapper {
    PasswordEncoder passwordEncoder;
    public  UserRegistrationResponse toUserRegistrationResponse(User user) {
        return UserRegistrationResponse.builder()
                .email(user.getEmail())
                .userId(user.getUserId())
                .name(user.getName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .build();
    }

    public User toEntity(UserRegistrationRequest user) {
        return User.builder()
                .email(user.getEmail())
                .name(user.getName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .passwordHash(passwordEncoder.encode(user.getPassword()))
                .build();
    }

    public UserLoginResponse toUserLoginResponse(User user, UserLoginRequestDto loginDetails) {
        String token = passwordEncoder.encode(loginDetails.getPassword());
        return UserLoginResponse.builder()
                .token(token)
                .userId(user.getUserId())
                .name(user.getName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole().name())
                .build();
    }   
}
