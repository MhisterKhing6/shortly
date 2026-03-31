package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "called_parcel_infos")
public class CalledParcelInfo {
    @Id
    private String id;
    private ParcelInfo parcelInfo;
    private String callerName;
    private String callerPhoneNumber;
    private String notes; // Any additional notes about the call
    private String officeId; // To associate with the office
    @CreatedDate
    private Long createdAt;

    @LastModifiedDate
    private Long updatedAt;
    
    private boolean homeDelivered;
}
