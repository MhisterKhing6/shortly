package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "email_verification_tokens")
public class EmailVerificationToken {

    @Id
    private String id;

    @Indexed(unique = true)
    private String token;

    @Indexed
    private String companyId;

    // The admin user created (disabled) at registration; activated when this token is verified.
    private String userId;

    private String email;

    private Long expiresAt;

    private boolean used = false;

    @CreatedDate
    private Long createdAt;
}
