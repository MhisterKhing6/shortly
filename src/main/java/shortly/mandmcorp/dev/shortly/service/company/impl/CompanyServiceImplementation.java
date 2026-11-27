package shortly.mandmcorp.dev.shortly.service.company.impl;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.dto.request.CompanyRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.response.CompanyRegistrationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.EmailVerificationResponse;
import shortly.mandmcorp.dev.shortly.enums.DepartmentRole;
import shortly.mandmcorp.dev.shortly.enums.UserRole;
import shortly.mandmcorp.dev.shortly.enums.UserStatusEnum;
import shortly.mandmcorp.dev.shortly.exceptions.EntityAlreadyExist;
import shortly.mandmcorp.dev.shortly.exceptions.EntityNotFound;
import shortly.mandmcorp.dev.shortly.model.Company;
import shortly.mandmcorp.dev.shortly.model.EmailVerificationToken;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.CompanyRepository;
import shortly.mandmcorp.dev.shortly.repository.EmailVerificationTokenRepository;
import shortly.mandmcorp.dev.shortly.repository.UserRepository;
import shortly.mandmcorp.dev.shortly.service.company.CompanyServiceInterface;
import shortly.mandmcorp.dev.shortly.service.email.EmailServiceInterface;
import shortly.mandmcorp.dev.shortly.utils.PhoneNumberUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompanyServiceImplementation implements CompanyServiceInterface {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailServiceInterface emailService;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Value("${app.email-verification-path}")
    private String emailVerificationPath;

    @Value("${app.email-verification-expiry-ms}")
    private long verificationExpiryMs;

    @Override
    public CompanyRegistrationResponse registerCompany(CompanyRegistrationRequest request) {
        log.info("Registering new company: {}", request.getCompanyName());

        if (!PhoneNumberUtils.hasCountryCode(request.getPhoneNumber())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Phone number must begin with a country code (e.g. +233...)");
        }

        if (companyRepository.existsByCompanyNameIgnoreCase(request.getCompanyName())) {
            throw new EntityAlreadyExist("A company with this name already exists");
        }

        if (companyRepository.existsByEmailIgnoreCase(request.getWorkEmail())) {
            throw new EntityAlreadyExist("A company with this email already exists");
        }

        if (companyRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new EntityAlreadyExist("A company with this phone number already exists");
        }

        if (userRepository.findByPhoneNumber(request.getPhoneNumber()) != null) {
            throw new EntityAlreadyExist("A user with this phone number already exists");
        }

        if (userRepository.findByEmail(request.getWorkEmail()) != null) {
            throw new EntityAlreadyExist("A user with this email already exists");
        }

        // Create the company in a disabled/unverified state.
        Company company = new Company();
        company.setCompanyName(request.getCompanyName());
        company.setDisplayName(request.getCompanyName());
        company.setManagerName(request.getRegisterUserName());
        company.setManagerPhoneNumber(request.getPhoneNumber());
        company.setEmail(request.getWorkEmail());
        company.setPhoneNumber(request.getPhoneNumber());
        company.setEmailVerified(false);
        company.setEnabled(false);
        company.setCreatedAt(System.currentTimeMillis());
        company.setUpdatedAt(System.currentTimeMillis());

        Company savedCompany = companyRepository.save(company);
        log.info("Company registered (pending verification) with ID: {}", savedCompany.getId());

        // Create the admin user now, but keep it unavailable until the email is verified.
        User admin = User.builder()
                .name(request.getRegisterUserName())
                .email(request.getWorkEmail())
                .phoneNumber(request.getPhoneNumber())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.ADMIN)
                .departmentRole(DepartmentRole.MANAGER)
                .status(UserStatusEnum.ACTIVE)
                .availability(false)
                .companyId(savedCompany.getId())
                .build();
        User savedAdmin = userRepository.save(admin);
        log.info("Admin user {} created (unavailable, pending verification) for company {}",
                savedAdmin.getUserId(), savedCompany.getId());

        // Persist a one-time verification token, then send the verification email on a background thread.
        issueAndSendVerification(savedCompany, savedAdmin.getUserId(), request.getRegisterUserName());

        return CompanyRegistrationResponse.builder()
                .companyId(savedCompany.getId())
                .companyName(savedCompany.getCompanyName())
                .displayName(savedCompany.getDisplayName())
                .email(savedCompany.getEmail())
                .enabled(savedCompany.isEnabled())
                .message("Company registered. A verification email has been sent to " + savedCompany.getEmail()
                        + ". Verify your email to activate the company and create your admin account.")
                .build();
    }

    @Override
    public EmailVerificationResponse verifyEmail(String token) {
        EmailVerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification token"));

        if (verificationToken.isUsed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This verification link has already been used");
        }

        if (verificationToken.getExpiresAt() != null && verificationToken.getExpiresAt() < System.currentTimeMillis()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This verification link has expired");
        }

        Company company = companyRepository.findById(verificationToken.getCompanyId())
                .orElseThrow(() -> new EntityNotFound("Company not found for this verification token"));

        // Guard against a race / double-click.
        if (company.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This company has already been verified");
        }

        // Email verified — activate the admin user (created disabled at registration) and enable the company.
        User admin = userRepository.findByUserId(verificationToken.getUserId());
        if (admin == null) {
            throw new EntityNotFound("Admin user not found for this verification token");
        }
        admin.setAvailability(true);
        admin.setUpdatedAt(System.currentTimeMillis());
        User savedAdmin = userRepository.save(admin);
        log.info("Admin user {} activated for verified company {}", savedAdmin.getUserId(), company.getId());

        company.setEmailVerified(true);
        company.setEnabled(true);
        company.setUpdatedAt(System.currentTimeMillis());
        companyRepository.save(company);

        verificationToken.setUsed(true);
        verificationTokenRepository.save(verificationToken);

        return EmailVerificationResponse.builder()
                .companyId(company.getId())
                .companyName(company.getCompanyName())
                .displayName(company.getDisplayName())
                .enabled(company.isEnabled())
                .adminUserId(savedAdmin.getUserId())
                .adminName(savedAdmin.getName())
                .adminEmail(savedAdmin.getEmail())
                .adminPhoneNumber(savedAdmin.getPhoneNumber())
                .adminRole(savedAdmin.getRole())
                .message("Email verified. Your company is now active and your admin account is enabled.")
                .build();
    }

    @Override
    public CompanyRegistrationResponse resendVerification(String email) {
        Company company = companyRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new EntityNotFound("No company registered with this email"));

        if (company.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This company's email is already verified");
        }

        // The admin user already exists (created disabled at registration); find it via the
        // most recent verification token so the new link points to the same user.
        EmailVerificationToken previous = verificationTokenRepository
                .findFirstByCompanyIdOrderByCreatedAtDesc(company.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No pending verification found for this company; please register again"));

        // Invalidate the old token so its link can no longer be used.
        previous.setUsed(true);
        verificationTokenRepository.save(previous);

        issueAndSendVerification(company, previous.getUserId(), company.getManagerName());

        return CompanyRegistrationResponse.builder()
                .companyId(company.getId())
                .companyName(company.getCompanyName())
                .displayName(company.getDisplayName())
                .email(company.getEmail())
                .enabled(company.isEnabled())
                .message("A new verification email has been sent to " + company.getEmail() + ".")
                .build();
    }

    /**
     * Creates a fresh one-time verification token referencing the (disabled) admin user,
     * logs the token/link (testing aid), and sends the verification email on a background thread.
     */
    private void issueAndSendVerification(Company company, String userId, String recipientName) {
        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setToken(UUID.randomUUID().toString());
        verificationToken.setCompanyId(company.getId());
        verificationToken.setUserId(userId);
        verificationToken.setEmail(company.getEmail());
        verificationToken.setExpiresAt(System.currentTimeMillis() + verificationExpiryMs);
        verificationToken.setUsed(false);
        verificationTokenRepository.save(verificationToken);

        String verificationLink = frontendBaseUrl + emailVerificationPath + "?token=" + verificationToken.getToken();

        // TESTING: log the token/link so verification can be tested while email delivery is unavailable.
        log.info("Email verification token for {}: {}", company.getEmail(), verificationToken.getToken());
        log.info("Email verification link for {}: {}", company.getEmail(), verificationLink);

        // Fire-and-forget on a background thread; failures are logged inside the async method.
        emailService.sendCompanyVerificationEmail(
                company.getEmail(),
                recipientName,
                company.getCompanyName(),
                verificationLink);
    }
}
