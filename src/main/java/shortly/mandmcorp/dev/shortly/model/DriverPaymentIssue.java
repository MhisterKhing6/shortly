package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A problem found by the driver-payments integrity check. Replaced on every run. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "driver_payment_issues")
public class DriverPaymentIssue {
    @Id
    private String id;
    private String companyId;
    private String officeId;
    /** PAID_LINE_WITHOUT_PAYOUT, GROSS_MISMATCH, NET_MISMATCH, LINE_ON_TWO_PAYOUTS, ADVANCE_REPAID_MISMATCH */
    private String type;
    private String detail;
    /** The fee line, payout or adjustment concerned. */
    private String refId;
    private long checkedAt;
}
