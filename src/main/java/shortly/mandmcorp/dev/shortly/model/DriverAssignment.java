package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;

import lombok.Data;

@Data
public class DriverAssignment {
    @Id
    private String id;
    private String driverPhoneNumber;
    private String driverName;
    @Indexed
    private String officeId;
    @Indexed
    private String companyId;
    @Indexed
    private String parcelId;

    private ParcelInfo parcelInfo;
    private boolean payed;
    private double  amount;

    private boolean isDelivered;

    private String whoPayedDriverPhoneNumber;
    private String whoPayedDriverName;

    @CreatedDate
    private Long createdAt;
    @LastModifiedDate
    private Long updatedAt;
}
    

