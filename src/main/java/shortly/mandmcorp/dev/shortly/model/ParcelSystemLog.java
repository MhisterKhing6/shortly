package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "parcel_system_logs")
public class ParcelSystemLog {
    @Id
    private String id;

    @Indexed
    private String parcelId;
    private ParcelInfo parcelInfo;
    private String pickUpTime;
    private String whoPickedUpName;
    private String whoPickedUpTelephoneNumber;
    private String frontDeskPersonellName;
    private String frontDeskPersonellPhoneNumber;

    @Indexed
    private String officeId;

    @CreatedDate
    private Long createdAt;
}
