package shortly.mandmcorp.dev.shortly.utils;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import shortly.mandmcorp.dev.shortly.enums.CounterPaymentMethod;
import shortly.mandmcorp.dev.shortly.model.CounterCharge;
import shortly.mandmcorp.dev.shortly.model.Parcel;

/**
 * Rules for a customer collecting a parcel at the counter (Part L). Mirrors the frontend's
 * src/utils/counterPickup.ts: amount due = inbound + storage + pickup fee + POD item value.
 * Delivery cost is not charged, because the customer came in.
 */
public final class CounterPickupRules {

    public static final String REASON_OTHER = "Other";
    private static final double TOLERANCE = 0.01;

    private CounterPickupRules() {}

    /** The breakdown of what the customer owes, with the given pickup fee. */
    public static List<CounterCharge> charges(Parcel parcel, double pickupFee) {
        List<CounterCharge> charges = new ArrayList<>();
        charges.add(new CounterCharge("inbound", "Inbound", round2(parcel.getInboundCost())));
        charges.add(new CounterCharge("storage", "Storage", round2(parcel.getStorageCost())));
        charges.add(new CounterCharge("pickup", "Pickup fee", round2(pickupFee)));
        if (parcel.isPOD()) {
            charges.add(new CounterCharge("pod", "Item value (POD)", round2(parcel.getItemCost())));
        }
        return charges;
    }

    public static double amountDue(List<CounterCharge> charges) {
        return round2(charges.stream().mapToDouble(CounterCharge::getAmount).sum());
    }

    /**
     * Validates the payment step. Returns the server's variance (amountPaid − amountDue).
     */
    public static double validatePayment(double serverAmountDue, Double sentAmountDue, double amountPaid,
                                         Double sentVariance, CounterPaymentMethod method, String momoReference,
                                         String varianceReason, String varianceNote) {
        if (sentAmountDue != null && Math.abs(sentAmountDue - serverAmountDue) > TOLERANCE) {
            throw bad("Amount due has changed: the server calculates GHC " + format(serverAmountDue)
                    + ". Reload the parcel and try again.");
        }
        if (amountPaid < 0) throw bad("amountPaid can't be negative");
        double variance = round2(amountPaid - serverAmountDue);
        if (sentVariance != null && Math.abs(sentVariance - variance) > TOLERANCE) {
            throw bad("varianceAmount must equal amountPaid - amountDue");
        }
        if (amountPaid > 0 && method == null) throw bad("paymentMethod (CASH or MOMO) is required when amountPaid > 0");
        if (method == CounterPaymentMethod.MOMO && isBlank(momoReference)) {
            throw bad("momoReference is required for mobile money");
        }
        if (Math.abs(variance) > TOLERANCE) {
            if (isBlank(varianceReason)) throw bad("A reason is required when the amount paid differs from the amount due");
            if (REASON_OTHER.equalsIgnoreCase(varianceReason.trim()) && isBlank(varianceNote)) {
                throw bad("A note is required when the reason is Other");
            }
        }
        return variance;
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String format(double v) {
        return String.format("%.2f", v);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
