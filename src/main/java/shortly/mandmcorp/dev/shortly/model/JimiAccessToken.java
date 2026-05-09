package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "jimi_access_tokens")
public class JimiAccessToken {

    // Fixed ID so all instances share one document via upsert
    public static final String SINGLETON_ID = "jimi-token";

    @Id
    private String id;

    private String accessToken;

    /** Unix epoch seconds at which the token should be considered expired. */
    private long expiresAt;
}
