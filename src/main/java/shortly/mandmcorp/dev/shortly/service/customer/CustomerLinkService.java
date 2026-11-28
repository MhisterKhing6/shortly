package shortly.mandmcorp.dev.shortly.service.customer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.model.CustomerAccess;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.utils.PhoneNumberUtils;

/** Tokens that let a receiver open /receive and book a delivery without a staff login. */
@Service
@Slf4j
public class CustomerLinkService {

    static final long LINK_TTL_MS = 14L * 24 * 3_600_000L;
    static final long SESSION_TTL_MS = 6L * 3_600_000L;

    private final MongoTemplate mongoTemplate;
    private final String frontendBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public CustomerLinkService(MongoTemplate mongoTemplate,
                               @Value("${app.frontend-base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.mongoTemplate = mongoTemplate;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    /** Adds "Track or book delivery: <frontend>/receive?t=<token>" to an SMS. Never fails the SMS. */
    public String appendLink(String message, Parcel parcel) {
        try {
            String token = issue(CustomerAccess.LINK, parcel.getRecieverPhoneNumber(), LINK_TTL_MS);
            if (token == null) return message;
            return message + "\nTrack or book delivery: " + frontendBaseUrl + "/receive?t=" + token;
        } catch (RuntimeException e) {
            log.warn("Could not create a customer link for parcel {}: {}", parcel.getParcelId(), e.getMessage());
            return message;
        }
    }

    /** Creates a token for the phone and returns it (only its hash is stored). Null for no phone. */
    public String issue(String kind, String phone, long ttlMs) {
        String normalized = PhoneNumberUtils.normalizeGhana(phone);
        if (normalized == null) return null;
        byte[] bytes = new byte[24]; // 192 bits
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long now = System.currentTimeMillis();
        mongoTemplate.insert(CustomerAccess.builder()
                .kind(kind).tokenHash(hash(token)).phoneNumber(normalized)
                .createdAt(new Date(now)).expiresAt(new Date(now + ttlMs)).build());
        return token;
    }

    /** The phone a link or session token belongs to; 404 if unknown or expired. */
    public String phoneFor(String token) {
        if (token == null || token.isBlank()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found or expired");
        CustomerAccess access = mongoTemplate.findOne(new Query(Criteria.where("tokenHash").is(hash(token.trim()))
                .and("kind").in(CustomerAccess.LINK, CustomerAccess.SESSION)
                .and("expiresAt").gt(new Date())), CustomerAccess.class);
        if (access == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found or expired");
        return access.getPhoneNumber();
    }

    /** 401 unless the token belongs to this phone. */
    public String requirePhone(String token, String phone) {
        String tokenPhone;
        try {
            tokenPhone = phoneFor(token);
        } catch (ResponseStatusException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your link has expired; request a new code");
        }
        String normalized = PhoneNumberUtils.normalizeGhana(phone);
        if (normalized == null || !normalized.equals(tokenPhone)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This link isn't for that phone number");
        }
        return tokenPhone;
    }

    // ---- one-time codes

    /** Sends at most 3 codes per phone per 15 minutes. Returns the code (the caller texts it). */
    public String newOtp(String phone) {
        String normalized = PhoneNumberUtils.normalizeGhana(phone);
        if (normalized == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phoneNumber is required");
        long now = System.currentTimeMillis();
        long recent = mongoTemplate.count(new Query(Criteria.where("kind").is(CustomerAccess.OTP)
                .and("phoneNumber").is(normalized).and("createdAt").gt(new Date(now - 15 * 60_000L))), CustomerAccess.class);
        if (recent >= 3) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many codes sent; wait 15 minutes and try again");
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        mongoTemplate.insert(CustomerAccess.builder().kind(CustomerAccess.OTP).tokenHash(hash(normalized + ":" + code))
                .phoneNumber(normalized).createdAt(new Date(now)).expiresAt(new Date(now + 5 * 60_000L)).build());
        return code;
    }

    /** Checks the newest live code for the phone; 5 wrong tries lock it. Returns a session token. */
    public String verifyOtp(String phone, String code) {
        String normalized = PhoneNumberUtils.normalizeGhana(phone);
        Query q = new Query(Criteria.where("kind").is(CustomerAccess.OTP).and("phoneNumber").is(normalized)
                .and("used").is(false).and("expiresAt").gt(new Date()))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        CustomerAccess otp = normalized == null ? null : mongoTemplate.findOne(q, CustomerAccess.class);
        if (otp == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The code has expired; request a new one");
        if (otp.getFailedAttempts() >= 5) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many wrong tries; request a new code");
        }
        if (code == null || !hash(normalized + ":" + code.trim()).equals(otp.getTokenHash())) {
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(otp.getId())),
                    new Update().inc("failedAttempts", 1), CustomerAccess.class);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wrong code");
        }
        long used = mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(otp.getId()).and("used").is(false)),
                new Update().set("used", true), CustomerAccess.class).getModifiedCount();
        if (used == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The code was already used");
        return issue(CustomerAccess.SESSION, normalized, SESSION_TTL_MS);
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
