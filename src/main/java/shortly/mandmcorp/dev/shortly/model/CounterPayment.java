package shortly.mandmcorp.dev.shortly.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import shortly.mandmcorp.dev.shortly.enums.CounterPaymentMethod;

/** Snapshot of the counter payment, embedded on the parcel so Parcel Search can show it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterPayment {
    private String collectionId;
    private double amountDue;
    private double amountPaid;
    private double varianceAmount;
    private CounterPaymentMethod paymentMethod;
    private String momoReference;
    private String varianceReason;
    private String varianceNote;
    private long pickedUpAt;
    private String businessDate;
    private String recordedByName;

    public static CounterPayment from(CounterCollection c) {
        return CounterPayment.builder()
                .collectionId(c.getId()).amountDue(c.getAmountDue()).amountPaid(c.getAmountPaid())
                .varianceAmount(c.getVarianceAmount()).paymentMethod(c.getPaymentMethod())
                .momoReference(c.getMomoReference()).varianceReason(c.getVarianceReason())
                .varianceNote(c.getVarianceNote()).pickedUpAt(c.getPickedUpAt())
                .businessDate(c.getBusinessDate()).recordedByName(c.getRecordedByName())
                .build();
    }
}
