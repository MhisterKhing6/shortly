package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.ReconcilationType;

@Data
@Document(collection = "reconcilations")
public class Reconcilations {
    @Id
    private String id;
    
    private String payer;

    private String  payedTo;

    private String assignmentId;

    private ReconcilationType type;

    
}
