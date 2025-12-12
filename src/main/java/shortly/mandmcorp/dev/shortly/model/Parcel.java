package shortly.mandmcorp.dev.shortly.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "parcels")
public class Parcel {
    @Id
    private String parcelId;
    private String parcelDescription;
    private boolean isPOD;
    private boolean isDelivered;
    private boolean isParcelAssigned;
    private double inboundCost;
    private double pickUpCost;
    private boolean isFragile;
    private double deliveryCost;
    private double storageCost;
    private String shelfNumber;
    private String hasCalled;

    private String inboudPayed;

    @DBRef
    private Contacts driver;

    @DBRef
    private Contacts receiver;

    @DBRef
    private Contacts sender;
    
    @DBRef
    private Office officeId;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;

}
