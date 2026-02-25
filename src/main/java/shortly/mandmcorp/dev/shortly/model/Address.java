package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;

import lombok.Data;

@Data
public class Address {
    @Id
    private String id;
    @Indexed
    private String name;
    @Indexed
    private String officeId;
    private double cost;
}
