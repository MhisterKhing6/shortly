package shortly.mandmcorp.dev.shortly.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.annotation.TrackUserAction;
import shortly.mandmcorp.dev.shortly.dto.request.AddOfficeToUserRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ForgetPasswordRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ResetPasswordRequest;
import shortly.mandmcorp.dev.shortly.dto.request.UserLoginRequestDto;
import shortly.mandmcorp.dev.shortly.dto.request.UserRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.request.UserUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.response.UserLoginResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserRegistrationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.enums.ContactType;
import shortly.mandmcorp.dev.shortly.exceptions.ActionNotAllowed;
import shortly.mandmcorp.dev.shortly.model.Contacts;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.Shelf;
import shortly.mandmcorp.dev.shortly.service.contact.ContactServiceInterface;
import shortly.mandmcorp.dev.shortly.service.office.OfficeServiceInterface;
import shortly.mandmcorp.dev.shortly.service.rider.RiderServiceInterface;
import shortly.mandmcorp.dev.shortly.service.user.UserServiceInterface;




@RestController
@Slf4j
@RequestMapping("/api-user")
@AllArgsConstructor
@Tag(name = "User Management", description = "APIs for user registration, login, and password reset")
public class UserController {
    private final UserServiceInterface userService;
    private final ContactServiceInterface contactService;
    private final OfficeServiceInterface officeService;
    private final shortly.mandmcorp.dev.shortly.service.customer.CustomerPortalService customerPortalService;
    private final RiderServiceInterface riderService;

     

    @PostMapping("/admin/register")
    @Operation(summary = "Register a new user", description = "Admin endpoint to register a new user in the system")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User registered successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input data"),
        @ApiResponse(responseCode = "409", description = "User already exists")
    })
    @TrackUserAction(action = "REGISTER_USER", description = "Admin registered a new user")
    public UserRegistrationResponse registerUser(@RequestBody @Valid UserRegistrationRequest userRegistrationRequest) {
        return  userService.register(userRegistrationRequest);
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user and return JWT token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @TrackUserAction(action = "USER_LOGIN", description = "User logged into the system")
    public UserLoginResponse userLogin(@RequestBody @Valid UserLoginRequestDto loginDetails) {
        return userService.login(loginDetails);
    }


    @GetMapping("/parcel-search")
    @Operation(summary = "search user parcel", description = "check to see if user parcel has arrived")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "return user result"),
    })
    public List<shortly.mandmcorp.dev.shortly.dto.response.CustomerViews.CustomerParcel> searParcel(@RequestParam String phoneNumber) {
        if(phoneNumber == null || phoneNumber.isEmpty()) {
            throw new ActionNotAllowed("phone number required");
        }
        // Customer view only: no staff phones, call-centre notes or company data.
        return customerPortalService.search(phoneNumber);
    }

    @GetMapping("/customer-links/{token}")
    @Operation(summary = "Customer link from the SMS", description = "The phone's open parcels (customer view). 404 if expired or unknown.")
    public shortly.mandmcorp.dev.shortly.dto.response.CustomerViews.LinkResponse customerLink(@org.springframework.web.bind.annotation.PathVariable String token) {
        return customerPortalService.link(token);
    }

    @PostMapping("/customer-otp/send")
    @Operation(summary = "Text a one-time code to the receiver", description = "6 digits, 5 minutes. At most 3 per phone per 15 minutes; 404 if the phone has no parcels.")
    public java.util.Map<String, String> sendCustomerOtp(@RequestBody shortly.mandmcorp.dev.shortly.dto.response.CustomerViews.PhoneRequest request) {
        return customerPortalService.sendOtp(request.getPhoneNumber());
    }

    @PostMapping("/customer-otp/verify")
    @Operation(summary = "Check the one-time code", description = "Returns a token like the SMS link's, valid a few hours. Locked after 5 wrong tries.")
    public java.util.Map<String, String> verifyCustomerOtp(@RequestBody shortly.mandmcorp.dev.shortly.dto.response.CustomerViews.PhoneRequest request) {
        return customerPortalService.verifyOtp(request.getPhoneNumber(), request.getCode());
    }

    @PostMapping("/delivery-requests/quote")
    @Operation(summary = "Price a customer delivery", description = "Station table + road distance; several parcels: dearest parcel's price + extraParcelFee each. Valid 30 minutes. 401 token/phone mismatch, 403 not their parcel, 409 finished/with a rider/already booked.")
    public shortly.mandmcorp.dev.shortly.dto.response.CustomerViews.QuoteResponse deliveryQuote(@RequestBody shortly.mandmcorp.dev.shortly.dto.response.CustomerViews.QuoteRequest request) {
        return customerPortalService.quote(request);
    }

    @PostMapping("/delivery-requests")
    @Operation(summary = "Book a paid customer delivery", description = "Verifies the Paystack payment (success, GHS, at least the quoted total, reference used once), then marks the parcels for home delivery as prepaid. If it can't be applied after payment, the request is kept as FLAGGED for the station.")
    public java.util.Map<String, String> bookDelivery(@RequestBody shortly.mandmcorp.dev.shortly.dto.response.CustomerViews.BookingRequest request) {
        return customerPortalService.book(request);
    }

    @PostMapping("/request-password-reset")
    @Operation(summary = "Request password reset", description = "Send OTP code to user's phone for password reset")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OTP sent successfully"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @TrackUserAction(action = "REQUEST_PASSWORD_RESET", description = "User requested password reset")
    public UserResponse requestPasswordReset(@RequestBody @Valid ForgetPasswordRequest fr) {
        return userService.requestPasswordReset(fr);
    }
    
     @GetMapping("/shelf/office/{id}")
    @Operation(summary = "get a list of office shelf", description = "An endpoint to add shelfs")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "shelf retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "office not found")
    })
    public List<Shelf> getshelffsOffice(@PathVariable String id) {
        return officeService.getOfficeShelf(id);
    }

    @GetMapping("/assignment/resend-confirmation-code/{id}")
    @Operation(summary = "get a list of office shelf", description = "An endpoint to add shelfs")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "shelf retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "office not found")
    })
    public UserResponse resendConfirmationCode(@PathVariable String id) {
        return riderService.resendConfirmationCodeToReceiver(id);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Reset user password using verification token and new password")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Password reset successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid token or expired"),
        @ApiResponse(responseCode = "404", description = "Token not found")
    })
    @TrackUserAction(action = "RESET_PASSWORD", description = "User reset their password")
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
    @TrackUserAction(action = "UPDATE_PROFILE", description = "User updated their profile")
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
 
    @PostMapping("/add-user-office")
    public UserResponse postMethodName(@RequestBody @Valid AddOfficeToUserRequest entity) {
        return userService.addOfficeToUser(entity);
    }
    
}
