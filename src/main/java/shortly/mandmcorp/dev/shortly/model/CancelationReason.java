package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Document(collection = "cancelation_reasons")
@Data
public class CancelationReason {
    @Id
    private String id;
    private String reason;
    private int count;

}
