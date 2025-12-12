package shortly.mandmcorp.dev.shortly.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.dto.request.ForgetPasswordRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ResetPasswordRequest;
import shortly.mandmcorp.dev.shortly.dto.request.UserLoginRequestDto;
import shortly.mandmcorp.dev.shortly.dto.request.UserRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.request.UserUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.response.UserLoginResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserRegistrationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.enums.ContactType;
import shortly.mandmcorp.dev.shortly.model.Contacts;
import shortly.mandmcorp.dev.shortly.service.contact.ContactServiceInterface;
import shortly.mandmcorp.dev.shortly.service.user.UserServiceInterface;



@RestController
@RequestMapping("/api-user")
@AllArgsConstructor
@Tag(name = "User Management", description = "APIs for user registration, login, and password reset")
public class UserController {
    private final UserServiceInterface userService;
    private final ContactServiceInterface contactService;

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


    @GetMapping("/contacts")
    @Operation(summary = "Search contacts", description = "Search contacts with various filters and pagination")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Contacts retrieved successfully")
    })
    public Page<Contacts> searchContacts(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) ContactType type,
            @RequestParam(required = false) String vehicleNumber,
            @RequestParam(required = false) String address,
            Pageable pageable) {
        return contactService.searchContacts(name, phoneNumber, email, type, vehicleNumber, address, pageable);
    }

    @PutMapping("/profile")
    @Operation(summary = "Update user profile", description = "Update authenticated user's profile information")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
        @ApiResponse(responseCode = "409", description = "Phone number already exists"),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    public UserResponse updateProfile(@RequestBody @Valid UserUpdateRequest updateRequest) {
        return userService.updateProfile(updateRequest);
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns application health status")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Application is healthy")
    })
    public String health() {
        return "OK Ok";
    }

    
}
