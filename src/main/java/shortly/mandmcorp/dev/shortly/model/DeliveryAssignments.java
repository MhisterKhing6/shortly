package shortly.mandmcorp.dev.shortly.model;

import java.time.LocalDateTime;

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
    
    private DeliveryStatus status;
    private long assignedAt;
    private long acceptedAt;
    private long completedAt;

    private boolean payed;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
}