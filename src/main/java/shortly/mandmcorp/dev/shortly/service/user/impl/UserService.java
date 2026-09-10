package shortly.mandmcorp.dev.shortly.service.user.impl;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.config.FrontEndServerConfig;
import shortly.mandmcorp.dev.shortly.config.security.JWTConfig;
import shortly.mandmcorp.dev.shortly.dto.request.AddOfficeToUserRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ForgetPasswordRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ResetPasswordRequest;
import shortly.mandmcorp.dev.shortly.dto.request.RiderStatusUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.UserLoginRequestDto;
import shortly.mandmcorp.dev.shortly.dto.request.UserRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.request.UserUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.response.UserLoginResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserRegistrationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.enums.DepartmentRole;
import shortly.mandmcorp.dev.shortly.enums.UserRole;
import shortly.mandmcorp.dev.shortly.enums.UserStatusEnum;
import shortly.mandmcorp.dev.shortly.exceptions.EntityAlreadyExist;
import shortly.mandmcorp.dev.shortly.exceptions.EntityNotFound;
import shortly.mandmcorp.dev.shortly.exceptions.WrongCredentialsException;
import shortly.mandmcorp.dev.shortly.model.Company;
import shortly.mandmcorp.dev.shortly.model.Office;
import shortly.mandmcorp.dev.shortly.model.RiderStatusModel;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.model.UserAction;
import shortly.mandmcorp.dev.shortly.model.VerificationToken;
import shortly.mandmcorp.dev.shortly.repository.CompanyRepository;
import shortly.mandmcorp.dev.shortly.repository.OfficeRepository;
import shortly.mandmcorp.dev.shortly.repository.RiderStatusRepository;
import shortly.mandmcorp.dev.shortly.repository.UserActionRepository;
import shortly.mandmcorp.dev.shortly.repository.UserRepository;
import shortly.mandmcorp.dev.shortly.repository.VerificationTokenRepository;
import shortly.mandmcorp.dev.shortly.service.email.EmailServiceInterface;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationInterface;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationRequestTemplate;
import shortly.mandmcorp.dev.shortly.service.user.UserServiceInterface;
import shortly.mandmcorp.dev.shortly.utils.NotificationUtil;
import shortly.mandmcorp.dev.shortly.utils.OtpUtil;
import shortly.mandmcorp.dev.shortly.utils.PhoneNumberUtils;
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
    private final OfficeRepository officeRepository;
    private final UserActionRepository userActionRepository;
    private final MongoTemplate mongoTemplate;
    private final CompanyRepository companyRepository;
    private final EmailServiceInterface emailService;


    public UserService(FrontEndServerConfig frontend, UserRepository userRepository, UserMapper userMapper, @Qualifier("smsNotification") NotificationInterface smsNotification,
    PasswordEncoder passwordEncoder, JWTConfig jwtConfig, VerificationTokenRepository verificationTokenRepository,
    RiderStatusRepository riderStatusRepository, OfficeRepository officeRepository,
    UserActionRepository userActionRepository, MongoTemplate mongoTemplate, CompanyRepository companyRepository,
    EmailServiceInterface emailService) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.notification = smsNotification;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwtConfig;
        this.verificationTokenRepository = verificationTokenRepository;
        this.frontendConfig = frontend;
        this.riderStatusRepository = riderStatusRepository;
        this.officeRepository = officeRepository;
        this.userActionRepository = userActionRepository;
        this.mongoTemplate = mongoTemplate;
        this.companyRepository = companyRepository;
        this.emailService = emailService;
    }

    /**
     * Registers ca new user with auto-generated password.
     * Sends login credentials via SMS.
     * 
     * @param userRequestDetails user registration details
     * @return UserRegistrationResponse with user info
     * @throws EntityAlreadyExist if user already exists
     */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public UserRegistrationResponse register(UserRegistrationRequest userRequestDetails) {
        // Only a company admin whose department role is MANAGER may register users.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User loggedInUser = (User) auth.getPrincipal();
        if (loggedInUser.getRole() != UserRole.ADMIN || loggedInUser.getDepartmentRole() != DepartmentRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only a company admin (manager) can register users");
        }

        if (!PhoneNumberUtils.hasCountryCode(userRequestDetails.getPhoneNumber())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Phone number must begin with a country code (e.g. +233...)");
        }

        if (userRepository.existsByPhoneNumber(userRequestDetails.getPhoneNumber())) {
            throw new EntityAlreadyExist("A user with this phone number already exists");
        }

        if (userRequestDetails.getEmail() != null
                && userRepository.existsByEmail(userRequestDetails.getEmail())) {
            throw new EntityAlreadyExist("A user with this email already exists");
        }

        String password = OtpUtil.generateUserPassword();
        userRequestDetails.setPassword(password);
        User newUser = userMapper.toEntity(userRequestDetails);
        // Scope the new user to the creating admin's company.
        newUser.setCompanyId(loggedInUser.getCompanyId());

        List<String> officeNames = new ArrayList<>();
        if (userRequestDetails.getRole() != UserRole.VENDOR) {
            List<String> officeIds = userRequestDetails.getOfficeIds();
            if (officeIds == null || officeIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "At least one officeId is required for role " + userRequestDetails.getRole().name());
            }

            // Validate every office exists AND belongs to the logged-in admin's company before saving.
            List<Office> offices = new ArrayList<>();
            for (String officeId : officeIds) {
                Office office = officeRepository.findById(officeId)
                        .orElseThrow(() -> new EntityNotFound("Office not found: " + officeId));

                if (!Objects.equals(office.getCompanyId(), loggedInUser.getCompanyId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Office " + officeId + " does not belong to your company");
                }
                offices.add(office);
                officeNames.add(office.getName());
            }
            newUser.setOfficeIds(officeIds);

            if (userRequestDetails.getRole() == UserRole.MANAGER) {
                userRepository.save(newUser);
                offices.forEach(office -> office.setManager(newUser));
                officeRepository.saveAll(offices);
            } else {
                userRepository.save(newUser);
            }
        } else {
            userRepository.save(newUser);
        }

        // Create rider status if user is a rider
        if(newUser.getRole() == UserRole.RIDER) {
            RiderStatusModel riderStatus = new RiderStatusModel();
            riderStatus.setRider(newUser);
            riderStatus.setRiderStatus(shortly.mandmcorp.dev.shortly.enums.RiderStatus.OFFLINE);
            riderStatusRepository.save(riderStatus);
        }

        // Send login credentials via SMS
        String message = NotificationUtil.loginCredentials(password, newUser.getPhoneNumber(), newUser.getName(), newUser.getRole().name());
        NotificationRequestTemplate loginCredentails =  NotificationRequestTemplate.builder().body(message).to(newUser.getPhoneNumber()).build();
        notification.send(loginCredentails);

        // Also send login credentials via email (async) if an email is present
        if (newUser.getEmail() != null && !newUser.getEmail().isBlank()) {
            emailService.sendUserCredentialsEmail(newUser.getEmail(), newUser.getName(),
                    newUser.getPhoneNumber(), password, newUser.getRole().name());
        }

        // Resolve the company name for the response
        String companyName = null;
        if (loggedInUser.getCompanyId() != null) {
            companyName = companyRepository.findById(loggedInUser.getCompanyId())
                    .map(Company::getCompanyName)
                    .orElse(null);
        }

        return userMapper.toUserRegistrationResponse(newUser, companyName, officeNames);
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

        // Get the first office if user has multiple offices
        Office office = null;
        if(userEntity.getOfficeIds() != null && !userEntity.getOfficeIds().isEmpty()) {
            office = officeRepository.findById(userEntity.getOfficeIds().get(0))
                .orElseThrow(()-> new EntityNotFound("office not found"));
        }

        // If the user belongs to a company, the company must be enabled (email verified) to log in.
        String companyName = null;
        log.info("Login company-check: user={} companyId={}", userEntity.getPhoneNumber(), userEntity.getCompanyId());
        if (userEntity.getCompanyId() != null) {
            Company company = companyRepository.findById(userEntity.getCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Company not found for this account"));

            log.info("Login company-check: companyId={} enabled={}", company.getId(), company.isEnabled());
            if (!company.isEnabled()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your company is not yet activated. Please verify your company email before logging in.");
            }
            companyName = company.getCompanyName();
        }

        return userMapper.toUserLoginResponse(userEntity, token, office, companyName);
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
        if( !token.getCode().equals(fr.getVerificationCode()) ) {
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
    /** Rejects (as not-found) if the target user isn't in the logged-in caller's company. */
    private void assertSameCompanyAsCaller(User target) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String companyId = (auth != null && auth.getPrincipal() instanceof User caller) ? caller.getCompanyId() : null;
        if (!java.util.Objects.equals(target.getCompanyId(), companyId)) {
            throw new EntityNotFound("User not found");
        }
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse deleteUser(String userId) {
        log.error("Attempting to delete user with ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFound("User not found"));
        assertSameCompanyAsCaller(user);
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
        assertSameCompanyAsCaller(user);
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

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public Page<User> getUsers(String officeId, Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User loggedInUser = (User) auth.getPrincipal();
        String companyId = loggedInUser.getCompanyId();

        if (officeId != null && !officeId.isBlank()) {
            return userRepository.findByCompanyIdAndOfficeIdsContaining(companyId, officeId, pageable);
        }
        return userRepository.findByCompanyId(companyId, pageable);
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
    @PreAuthorize("hasRole('RIDER')")
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

    @Override
    @PreAuthorize("hasRole('MANAGER') or hasRole('FRONTDESK')")
    public List<User> getRidersByOfficeId(boolean availability) {
         Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        User user = (User) auth.getPrincipal();

        // Get riders from the user's first office (or all offices if needed)
        if(user.getOfficeIds() == null || user.getOfficeIds().isEmpty()) {
            throw new WrongCredentialsException("User has no associated offices");
        }

        return userRepository.findByRoleAndOfficeIdsContainingAndAvailability(UserRole.RIDER, user.getOfficeIds().get(0), availability);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('FRONTDESK')")
    public UserResponse addOfficeToUser(AddOfficeToUserRequest request) {
        User user = userRepository.findByPhoneNumber(request.getUserPhoneNumber());

        if(user == null) {
            throw new EntityNotFound("User not found");
        }

        List<String> officeIds = user.getOfficeIds();
        if(officeIds == null) {
            officeIds = new java.util.ArrayList<>();
        }

        if(!officeIds.contains(request.getOfficeId())) {
            Office office = officeRepository.findById(request.getOfficeId())
                .orElseThrow(() -> new EntityNotFound("Office not found: " + request.getOfficeId()));
            officeIds.add(request.getOfficeId());
            user.setOfficeIds(officeIds);
            userRepository.save(user);
        }
        return new UserResponse("Office added successfully", user.getPhoneNumber());
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public Page<UserAction> getUserActions(String userEmail, String officeId, String phoneNumber, Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User loggedInUser = (User) auth.getPrincipal();

        Query query = new Query();
        query.addCriteria(Criteria.where("companyId").is(loggedInUser.getCompanyId()));

        if (userEmail != null && !userEmail.isBlank()) {
            query.addCriteria(Criteria.where("userEmai").is(userEmail));
        }

        if (officeId != null && !officeId.isBlank()) {
            query.addCriteria(Criteria.where("officeId").is(officeId));
        }

        if (phoneNumber != null && !phoneNumber.isBlank()) {
            query.addCriteria(Criteria.where("phoneNumber").is(phoneNumber));
        }

        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        long total = mongoTemplate.count(query, UserAction.class);

        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        return new PageImpl<>(mongoTemplate.find(query, UserAction.class), pageable, total);
    }

}