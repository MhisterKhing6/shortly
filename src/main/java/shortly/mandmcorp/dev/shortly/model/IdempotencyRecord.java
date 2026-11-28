package shortly.mandmcorp.dev.shortly.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of a request sent with an Idempotency-Key, kept 24 hours so a retry returns the same result. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "idempotency_keys")
public class IdempotencyRecord {
    /** scope:companyId:userId:key */
    @Id
    private String id;
    /** PENDING while the first request runs, then DONE. */
    private String status;
    @Builder.Default
    private List<String> resultIds = new ArrayList<>();
    private String message;
    /** TTL index removes the record 24 hours after creation. */
    private Date createdAt;
}
