package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;

public class Reconcilations {
    @Id
    private String id;
    
    @DBRef
    private User payer;

    private String  payedTo;

    private String ammount;

    
}
