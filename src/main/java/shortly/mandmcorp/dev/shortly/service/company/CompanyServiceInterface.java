package shortly.mandmcorp.dev.shortly.service.company;

import shortly.mandmcorp.dev.shortly.dto.request.CompanyRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.response.CompanyRegistrationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.EmailVerificationResponse;

public interface CompanyServiceInterface {
    CompanyRegistrationResponse registerCompany(CompanyRegistrationRequest request);
    EmailVerificationResponse verifyEmail(String token);
    CompanyRegistrationResponse resendVerification(String email);
}
