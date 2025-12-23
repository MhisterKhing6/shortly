package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;

import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.ReconcilationType;

@Data
public class Reconcilations {
    @Id
    private String id;
    
    @DBRef
    private User payer;

    @DBRef
    private User  payedTo;

    private String amount;

    
    private String assignmentId;

    private ReconcilationType type;

    
}
