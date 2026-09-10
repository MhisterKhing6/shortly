package shortly.mandmcorp.dev.shortly.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.annotation.TrackUserAction;
import shortly.mandmcorp.dev.shortly.dto.request.CompanyRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.request.EmailVerificationRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ResendVerificationRequest;
import shortly.mandmcorp.dev.shortly.dto.response.CompanyRegistrationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.EmailVerificationResponse;
import shortly.mandmcorp.dev.shortly.service.company.CompanyServiceInterface;

@RestController
@AllArgsConstructor
@RequestMapping("/api-company")
@Tag(name = "Company Management", description = "APIs for company registration and email verification")
public class CompanyController {

    private final CompanyServiceInterface companyService;

    @PostMapping("/register")
    @Operation(summary = "Register a new company", description = "Public endpoint to register a company. Creates the company in a disabled state and emails a frontend verification link. The ADMIN user is created only after the email is verified.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Company registered; verification email sent"),
        @ApiResponse(responseCode = "400", description = "Passwords do not match or invalid input"),
        @ApiResponse(responseCode = "409", description = "Company or user already exists")
    })
    @TrackUserAction(action = "REGISTER_COMPANY", description = "A new company was registered")
    public CompanyRegistrationResponse registerCompany(@RequestBody @Valid CompanyRegistrationRequest request) {
        return companyService.registerCompany(request);
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify company email", description = "Public endpoint the frontend calls with the token extracted from the verification link. Enables the company and creates the ADMIN user.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Email verified; company activated and admin created"),
        @ApiResponse(responseCode = "400", description = "Invalid, expired or already-used token")
    })
    @TrackUserAction(action = "VERIFY_COMPANY_EMAIL", description = "A company email was verified")
    public EmailVerificationResponse verifyEmail(@RequestBody @Valid EmailVerificationRequest request) {
        return companyService.verifyEmail(request.getToken());
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification email", description = "Public endpoint to resend the verification email for a company whose email is not yet verified. Invalidates the previous verification link.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Verification email resent"),
        @ApiResponse(responseCode = "400", description = "Email already verified or no pending verification"),
        @ApiResponse(responseCode = "404", description = "No company registered with this email")
    })
    @TrackUserAction(action = "RESEND_COMPANY_VERIFICATION", description = "A company verification email was resent")
    public CompanyRegistrationResponse resendVerification(@RequestBody @Valid ResendVerificationRequest request) {
        return companyService.resendVerification(request.getEmail());
    }
}
