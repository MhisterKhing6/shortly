package shortly.mandmcorp.dev.shortly.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import shortly.mandmcorp.dev.shortly.enums.CounterPaymentMethod;

/** Money taken at the counter when a customer collects a parcel (Part L). One per parcel. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "counter_collections")
@CompoundIndexes({
    @CompoundIndex(name = "office_day_idx", def = "{'companyId': 1, 'officeId': 1, 'businessDate': 1, 'pickedUpAt': 1}")
})
public class CounterCollection {
    @Id
    private String id;

    @Indexed(unique = true)
    private String parcelId;

    @Indexed
    private String companyId;
    private String officeId;
    /** Station-local calendar day (yyyy-MM-dd) whose cash-up this belongs to. */
    private String businessDate;
    private long pickedUpAt;

    private String receiverName;
    private String collectedByName;
    private String collectedByPhone;

    private double pickUpCost;
    /** Server-computed: inbound + storage + pickup fee + POD item value. */
    private double amountDue;
    private double amountPaid;
    private double varianceAmount;
    private CounterPaymentMethod paymentMethod;
    private String momoReference;
    private String varianceReason;
    private String varianceNote;
    @Builder.Default
    private List<CounterCharge> charges = new ArrayList<>();

    /** POD item value collected for the sender/vendor (0 if not POD). */
    private double podAmount;
    /** True once the POD value has been paid on to the sender/vendor. */
    private boolean podPaidOut;
    // Set when the POD value is paid on (POST /api-admin/pod-payouts)
    private Long podPaidAt;
    private String podPaidById;
    private String podPaidByName;
    private CounterPaymentMethod podPaymentMethod;
    private String podMomoReference;
    private String podPaidTo;
    private String podPayoutNote;
    /** Station day whose drawer a cash POD payout came out of. */
    private String podPayoutBusinessDate;

    private String recordedById;
    private String recordedByName;
}
