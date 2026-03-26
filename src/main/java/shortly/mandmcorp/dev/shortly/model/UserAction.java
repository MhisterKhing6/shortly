package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.CreatedDate;
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
@Document(collection = "user-actions")
public class UserAction {
    @Id
    private String id;

    private String userId;

    private String userEmai;

    private String userName;

    private String action;

    private String description;

    private String officeId;

    private String phoneNumber;
    
    @CreatedDate
    private Long createdAt;
}
