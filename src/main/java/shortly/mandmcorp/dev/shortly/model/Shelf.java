package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Document(collation = "shelfs")
@Data
public class Shelf {
    @Id
    private String id;

    @DBRef
    private Office office;

    private String name;
}
