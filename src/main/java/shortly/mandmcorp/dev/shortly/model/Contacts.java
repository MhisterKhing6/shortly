package shortly.mandmcorp.dev.shortly.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Builder;
import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.ContactType;

@Data
@Builder
@Document(collection = "contacts")
public class Contacts {
    @Id
    @Field("_id")
    private String id;
    private String email;
    private String phoneNumber;
    private String address;
    private String name;
    private String vehicleNumber;
    private ContactType type;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
