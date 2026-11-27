package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import shortly.mandmcorp.dev.shortly.enums.CounterPaymentMethod;
import shortly.mandmcorp.dev.shortly.enums.DriverAdjustmentType;

/**
 * An advance given to a driver, or a deduction/bonus to apply at the next payout. Open until a
 * payout applies (or, for an advance, recovers) it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "driver_adjustments")
public class DriverAdjustment {
    @Id
    private String id;
    private String companyId;
    private String officeId;
    @Indexed
    private String driverId;
    private DriverAdjustmentType type;
    private double amount;
    private String reason;
    private String parcelId;
    /** For an ADVANCE: how the cash went out (CASH comes out of the counter drawer). */
    private CounterPaymentMethod paymentMethod;
    private String businessDate;
    private boolean open;
    /** For an ADVANCE: how much has been recovered through payouts so far. */
    private double repaidAmount;
    private String appliedPayoutId;
    private Long appliedAt;
    private long createdAt;
    private String createdById;
    private String createdByName;
}
