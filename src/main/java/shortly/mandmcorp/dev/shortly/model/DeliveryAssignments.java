package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;

@Data
@Document(collection = "delivery_assignments")
public class DeliveryAssignments {
    @Id
    private String assignmentId;
    
    @DBRef
    private User riderId;
    
    @DBRef
    private Parcel orderId;
    
    private String officeId;
    private DeliveryStatus status;
    private long assignedAt;
    private long acceptedAt;
    private long completedAt;
    private String payementMethod;

    private boolean payed;

    private String confirmationCode;

    
    private String cancelationReason;

    @DBRef
    private User completedBy;
    
    @CreatedDate
    private Long createdAt;
    
    @LastModifiedDate
    private Long updatedAt;
}