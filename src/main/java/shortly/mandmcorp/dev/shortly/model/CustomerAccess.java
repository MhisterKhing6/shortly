package shortly.mandmcorp.dev.shortly.model;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Customer access for the public /receive page: an SMS link token (14 days), a session token from
 * a verified one-time code (a few hours), or a pending one-time code (5 minutes). Only SHA-256
 * hashes of tokens and codes are stored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "customer_access")
public class CustomerAccess {
    public static final String LINK = "LINK";
    public static final String SESSION = "SESSION";
    public static final String OTP = "OTP";

    @Id
    private String id;
    private String kind;
    @Indexed
    private String tokenHash;
    @Indexed
    private String phoneNumber;
    private Date createdAt;
    /** TTL index removes the record after this time. */
    private Date expiresAt;
    /** OTP only. */
    private int failedAttempts;
    private boolean used;
}
