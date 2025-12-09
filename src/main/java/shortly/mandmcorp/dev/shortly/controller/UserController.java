package shortly.mandmcorp.dev.shortly.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.dto.request.ForgetPasswordRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ResetPasswordRequest;
import shortly.mandmcorp.dev.shortly.dto.request.UserLoginRequestDto;
import shortly.mandmcorp.dev.shortly.dto.request.UserRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.response.UserLoginResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserRegistrationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.service.user.UserServiceInterface;



@RestController
@RequestMapping("/api-user")
@AllArgsConstructor
@Tag(name = "User Management", description = "APIs for user registration, login, and password reset")
public class UserController {
    private final UserServiceInterface userService;

    @PostMapping("/admin/register")
    @Operation(summary = "Register a new user", description = "Admin endpoint to register a new user in the system")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User registered successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input data"),
        @ApiResponse(responseCode = "409", description = "User already exists")
    })
    public UserRegistrationResponse registerUser(@RequestBody @Valid UserRegistrationRequest userRegistrationRequest) {
        return  userService.register(userRegistrationRequest);
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user and return JWT token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public UserLoginResponse userLogin(@RequestBody @Valid UserLoginRequestDto loginDetails) {
        return userService.login(loginDetails);
    }

    @PostMapping("/request-password-reset")
    @Operation(summary = "Request password reset", description = "Send OTP code to user's phone for password reset")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OTP sent successfully"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public UserResponse requestPasswordReset(@RequestBody @Valid ForgetPasswordRequest fr) {
        return userService.requestPasswordReset(fr);
    }
    

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Reset user password using verification token and new password")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Password reset successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid token or expired"),
        @ApiResponse(responseCode = "404", description = "Token not found")
    })
    public UserResponse resetPassword(@RequestBody @Valid ResetPasswordRequest fr) {
        return userService.resetPassword(fr);
    }
}
