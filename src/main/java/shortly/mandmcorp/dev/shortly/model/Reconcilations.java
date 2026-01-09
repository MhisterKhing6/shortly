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

    private String parcelId;


    private ReconcilationType type;

    private boolean isCompleted = false;

    private double amount;

    private String riderName;
    private String riderId;
    private String officeId;
    private String riderPhoneNumber;

    private Long createdAt;
    private Long reconciledAt;




}

