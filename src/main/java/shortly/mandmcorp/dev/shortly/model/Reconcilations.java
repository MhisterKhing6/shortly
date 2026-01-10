package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.ReconcilationType;

@Data
@Document(collection = "reconcilations")
@CompoundIndexes({
    @CompoundIndex(name = "office_completed_idx", def = "{'officeId': 1, 'isCompleted': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "office_created_idx", def = "{'officeId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "rider_completed_idx", def = "{'riderId': 1, 'isCompleted': 1}")
})
public class Reconcilations {
    @Id
    private String id;

    private String payer;

    private String  payedTo;

    @Indexed(unique = true)
    private String assignmentId;

    private String parcelId;


    private ReconcilationType type;

    private boolean isCompleted = false;

    private double amount;

    private String riderName;
    private String riderId;

    @Indexed
    private String officeId;
    private String riderPhoneNumber;

    @Indexed
    private Long createdAt;
    private Long reconciledAt;




}

