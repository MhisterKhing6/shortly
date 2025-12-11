package shortly.mandmcorp.dev.shortly.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "offices")
public class Office {

    @Id
    private String id;

    @Indexed
    private String name;

    @Indexed(unique = true)
    private String code; 

    private String address;
    
    @DBRef
    private Location location;

    @DBRef
    private User manager;

    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
