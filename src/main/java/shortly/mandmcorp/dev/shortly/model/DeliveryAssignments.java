package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;

@Data
@Document(collection = "delivery_assignmentss")
@CompoundIndexes({
    @CompoundIndex(name = "rider_status_idx", def = "{'riderInfo.riderId': 1, 'status': 1}"),
    @CompoundIndex(name = "office_status_idx", def = "{'officeId': 1, 'status': 1}"),
    @CompoundIndex(name = "office_payed_idx", def = "{'officeId': 1, 'payed': 1}"),
    @CompoundIndex(name = "rider_phone_status_idx", def = "{'riderInfo.riderId': 1, 'parcelInfo.receiverPhoneNumber': 1, 'status': 1}"),
    @CompoundIndex(name = "office_assigned_idx", def = "{'officeId': 1, 'assignedAt': -1}")
})
public class DeliveryAssignments {
    @Id
    private String assignmentId;

    private RiderInfo riderInfo;

    private ParcelInfo parcelInfo;

    @Indexed
    private String officeId;

    @Indexed
    private DeliveryStatus status;

    @Indexed
    private long assignedAt;
    private long acceptedAt;
    private long completedAt;
    private String payementMethod;

    private boolean payed;

    private String confirmationCode;


    private String cancelationReason;

    //private String completedBy;

    @CreatedDate
    private Long createdAt;

    @LastModifiedDate
    private Long updatedAt;
}