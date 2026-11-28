package shortly.mandmcorp.dev.shortly.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import shortly.mandmcorp.dev.shortly.enums.CounterPaymentMethod;
import shortly.mandmcorp.dev.shortly.enums.DriverAdjustmentType;

/** A payment to a driver settling one or more READY fee lines. Reversals keep the record. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "driver_payouts")
public class DriverPayout {
    @Id
    private String id;
    /** Per station, sequential: DP-00431. */
    private String receiptNo;
    private String companyId;
    @Indexed
    private String officeId;
    @Indexed
    private String driverId;
    private String driverName;
    private String driverPhone;
    @Builder.Default
    private List<String> lineIds = new ArrayList<>();
    private int tripCount;
    @Builder.Default
    private List<DriverPayoutAdjustment> adjustments = new ArrayList<>();
    private double grossAmount;
    private double deductions;
    private double bonuses;
    private double netAmount;
    private CounterPaymentMethod paymentMethod;
    private String momoNumber;
    private String momoReference;
    private String note;
    private String businessDate;
    private String paidById;
    private String paidByName;
    private long paidAt;

    private boolean confirmed;
    private Long confirmedAt;
    @JsonIgnore
    private String confirmationCode;
    @JsonIgnore
    private Long confirmationCodeSentAt;

    private boolean reversed;
    private String reversedReason;
    private Long reversedAt;
    private String reversedByName;

    /** True when created by the old PUT driver-assignments/pay endpoint. */
    private boolean legacy;

    /** Driver's balance right after this payout, for the receipt. Not stored. */
    @org.springframework.data.annotation.Transient
    private Double balanceReadyAfter;
    @org.springframework.data.annotation.Transient
    private Double balanceWaitingAfter;

    /** Same as id; the frontend reads payoutId. */
    public String getPayoutId() {
        return id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriverPayoutAdjustment {
        private DriverAdjustmentType type;
        private double amount;
        private String reason;
        private String parcelId;
        /** The DriverAdjustment this applied, if any. */
        private String adjustmentId;
    }
}
