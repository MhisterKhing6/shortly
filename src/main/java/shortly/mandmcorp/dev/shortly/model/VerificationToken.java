package shortly.mandmcorp.dev.shortly.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Document(collection = "verification_tokens")
public class VerificationToken {
    
    @Id
    private String id;

    @DBRef
    private User userId;
    
    @NotNull
    private LocalDateTime createdAt;
}
