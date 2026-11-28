package shortly.mandmcorp.dev.shortly.service.vendor;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import shortly.mandmcorp.dev.shortly.dto.request.UserRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.request.VendorApplicationRequests.Apply;
import shortly.mandmcorp.dev.shortly.dto.request.VendorApplicationRequests.Review;
import shortly.mandmcorp.dev.shortly.dto.response.UserRegistrationResponse;
import shortly.mandmcorp.dev.shortly.enums.UserRole;
import shortly.mandmcorp.dev.shortly.model.Company;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.model.VendorApplication;
import shortly.mandmcorp.dev.shortly.repository.UserRepository;
import shortly.mandmcorp.dev.shortly.service.user.impl.UserService;
import shortly.mandmcorp.dev.shortly.utils.CurrentUser;
import shortly.mandmcorp.dev.shortly.utils.PhoneNumberUtils;

/** "Become a partner" requests: public submission, admin follow-up, optional vendor account. */
@Service
public class VendorApplicationService {

    private static final Set<String> REVIEW_STATUSES = Set.of("CONTACTED", "APPROVED", "REJECTED");

    private final MongoTemplate mongoTemplate;
    private final UserService userService;
    private final UserRepository userRepository;

    public VendorApplicationService(MongoTemplate mongoTemplate, UserService userService, UserRepository userRepository) {
        this.mongoTemplate = mongoTemplate;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    /** Public. At most 3 requests per phone per day. The company is told from the site's domain. */
    public Map<String, String> apply(Apply req, String origin) {
        if (isBlank(req.getBusinessName())) throw bad("businessName is required");
        if (isBlank(req.getContactName())) throw bad("contactName is required");
        if (isBlank(req.getArea())) throw bad("area is required");
        String phone = PhoneNumberUtils.normalizeGhana(req.getPhoneNumber());
        if (phone == null || phone.length() < 10) throw bad("A valid phoneNumber is required");
        if (req.getEmail() != null && !req.getEmail().isBlank() && !req.getEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw bad("email is not valid");
        }
        if (req.getParcelsPerWeek() != null && req.getParcelsPerWeek() < 0) throw bad("parcelsPerWeek can't be negative");
        if (length(req.getMessage()) > 2000 || length(req.getBusinessName()) > 200 || length(req.getContactName()) > 200) {
            throw bad("Some fields are too long");
        }
        long dayAgo = System.currentTimeMillis() - 86_400_000L;
        long recent = mongoTemplate.count(new Query(Criteria.where("phoneNumber").is(phone).and("createdAt").gt(dayAgo)), VendorApplication.class);
        if (recent >= 3) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "We already have your request; we'll be in touch");
        }
        String host = hostOf(origin);
        VendorApplication app = mongoTemplate.insert(VendorApplication.builder()
                .companyId(companyForHost(host))
                .businessName(req.getBusinessName().trim()).contactName(req.getContactName().trim())
                .phoneNumber(phone).email(blankToNull(req.getEmail())).area(req.getArea().trim())
                .parcelsPerWeek(req.getParcelsPerWeek()).message(blankToNull(req.getMessage()))
                .status(VendorApplication.NEW).sourceHost(host).createdAt(System.currentTimeMillis())
                .build());
        return Map.of("message", "Thanks! We'll contact you soon.", "id", app.getId());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public List<VendorApplication> list(String status) {
        User user = CurrentUser.get();
        Criteria c = user.getRole() == UserRole.SUPERADMIN
                ? new Criteria().orOperator(Criteria.where("companyId").is(user.getCompanyId()), Criteria.where("companyId").is(null))
                : Criteria.where("companyId").is(user.getCompanyId());
        if (status != null && !status.isBlank()) c = new Criteria().andOperator(c, Criteria.where("status").is(status.toUpperCase()));
        return mongoTemplate.find(new Query(c).with(Sort.by(Sort.Direction.DESC, "createdAt")), VendorApplication.class);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public VendorApplication review(String id, Review req) {
        User user = CurrentUser.get();
        VendorApplication app = mongoTemplate.findById(id, VendorApplication.class);
        if (app == null || !(java.util.Objects.equals(app.getCompanyId(), user.getCompanyId())
                || (app.getCompanyId() == null && user.getRole() == UserRole.SUPERADMIN))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }
        String status = req.getStatus() == null ? null : req.getStatus().trim().toUpperCase();
        if (status == null || !REVIEW_STATUSES.contains(status)) throw bad("status must be CONTACTED, APPROVED or REJECTED");
        app.setStatus(status);
        app.setAdminNote(req.getNote());
        app.setHandledById(user.getUserId());
        app.setHandledByName(user.getName());
        app.setHandledAt(System.currentTimeMillis());
        if (app.getCompanyId() == null) app.setCompanyId(user.getCompanyId());

        if ("APPROVED".equals(status) && req.isCreateVendor() && app.getVendorUserId() == null) {
            User existing = userRepository.findByPhoneNumber(app.getPhoneNumber());
            if (existing != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with phone " + app.getPhoneNumber() + " already exists");
            }
            UserRegistrationRequest r = new UserRegistrationRequest();
            r.setName(app.getBusinessName());
            r.setEmail(app.getEmail());
            r.setPhoneNumber(app.getPhoneNumber());
            r.setRole(UserRole.VENDOR);
            r.setOfficeIds(req.getOfficeIds());
            UserRegistrationResponse created = userService.register(r);
            User vendor = userRepository.findByPhoneNumber(app.getPhoneNumber());
            app.setVendorUserId(vendor != null ? vendor.getUserId() : null);
        }
        return mongoTemplate.save(app);
    }

    /** The company whose site domain matches the request's Origin; the only company if there is one. */
    private String companyForHost(String host) {
        if (host != null) {
            Company byDomain = mongoTemplate.findOne(new Query(Criteria.where("domains").is(host)), Company.class);
            if (byDomain != null) return byDomain.getId();
        }
        List<Company> enabled = mongoTemplate.find(new Query(Criteria.where("enabled").is(true)).limit(2), Company.class);
        return enabled.size() == 1 ? enabled.get(0).getId() : null;
    }

    private static String hostOf(String origin) {
        if (origin == null || origin.isBlank()) return null;
        try {
            String host = URI.create(origin.trim()).getHost();
            return host == null ? null : host.toLowerCase().replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int length(String s) {
        return s == null ? 0 : s.length();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }
}
