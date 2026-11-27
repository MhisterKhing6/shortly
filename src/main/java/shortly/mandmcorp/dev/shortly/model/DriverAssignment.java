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

    // ---- Driver fee line (driver payments) ----
    @Indexed
    private String driverId;
    @Indexed
    private String tripId;
    /** Replaces payed; payed is kept equal to (state == PAID) for old clients. */
    @Indexed
    private shortly.mandmcorp.dev.shortly.enums.DriverFeeState state;
    private Long readyAt;
    /** e.g. "PICKED_UP 11:05" or "DELIVERED 12:40". */
    private String readyReason;
    private String holdReason;
    private String heldBy;
    private Long heldAt;
    /** State to return to when a hold is released. */
    private shortly.mandmcorp.dev.shortly.enums.DriverFeeState stateBeforeHold;
    @Indexed
    private String payoutId;
    private Long paidAt;

    private String whoPayedDriverPhoneNumber;
    private String whoPayedDriverName;

    @CreatedDate
    private Long createdAt;
    @LastModifiedDate
    private Long updatedAt;
}
    

