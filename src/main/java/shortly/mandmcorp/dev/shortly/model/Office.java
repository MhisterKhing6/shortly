package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;
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

    private Location location;

    @DBRef
    private User manager;

    @Indexed
    private long createdAt;
}
