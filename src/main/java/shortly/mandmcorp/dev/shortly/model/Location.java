package shortly.mandmcorp.dev.shortly.model;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "locations")
public class Location {
    @Id
    private String id;

    private String name;     
    private String region;
    private String country;
    
    @CreatedDate
    private Long createdAt;
    
    @LastModifiedDate
    private Long updatedAt;

}

