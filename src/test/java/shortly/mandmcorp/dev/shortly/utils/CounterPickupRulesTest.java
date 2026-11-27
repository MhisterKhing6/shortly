package shortly.mandmcorp.dev.shortly.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import shortly.mandmcorp.dev.shortly.enums.CounterPaymentMethod;
import shortly.mandmcorp.dev.shortly.model.CounterCharge;
import shortly.mandmcorp.dev.shortly.model.Parcel;

class CounterPickupRulesTest {

    private static Parcel parcel(boolean pod) {
        Parcel p = new Parcel();
        p.setInboundCost(20);
        p.setStorageCost(5);
        p.setDeliveryCost(30); // not charged at the counter
        p.setPOD(pod);
        p.setItemCost(100);
        return p;
    }

    @Test
    void amountDueIsInboundStoragePickupAndPodValueButNotDelivery() {
        List<CounterCharge> plain = CounterPickupRules.charges(parcel(false), 10);
        assertEquals(35, CounterPickupRules.amountDue(plain));
        assertEquals(List.of("inbound", "storage", "pickup"), plain.stream().map(CounterCharge::getKey).toList());

        List<CounterCharge> pod = CounterPickupRules.charges(parcel(true), 10);
        assertEquals(135, CounterPickupRules.amountDue(pod));
        assertEquals("pod", pod.get(3).getKey());
    }

    @Test
    void acceptsExactCashAndWaivedPayment() {
        assertEquals(0, CounterPickupRules.validatePayment(35, 35.0, 35, 0.0, CounterPaymentMethod.CASH, null, null, null));
        assertEquals(-35, CounterPickupRules.validatePayment(35, null, 0, null, null, null, "Charge waived by manager", null));
        assertEquals(-5, CounterPickupRules.validatePayment(30, 30.0, 25, -5.0, CounterPaymentMethod.MOMO, "7712049935",
                "Other", "Will bring the rest tomorrow"));
    }

    @Test
    void rejectsBadPayments() {
        assertBad(() -> CounterPickupRules.validatePayment(35, 30.0, 30, null, CounterPaymentMethod.CASH, null, "x", null)); // due changed
        assertBad(() -> CounterPickupRules.validatePayment(35, null, 35, null, null, null, null, null));          // no method
        assertBad(() -> CounterPickupRules.validatePayment(35, null, 35, null, CounterPaymentMethod.MOMO, " ", null, null)); // no MoMo ref
        assertBad(() -> CounterPickupRules.validatePayment(35, null, 30, null, CounterPaymentMethod.CASH, null, null, null)); // no reason
        assertBad(() -> CounterPickupRules.validatePayment(35, null, 30, null, CounterPaymentMethod.CASH, null, "Other", "")); // Other w/o note
        assertBad(() -> CounterPickupRules.validatePayment(35, null, 30, -4.0, CounterPaymentMethod.CASH, null, "Customer paid less", null)); // wrong variance
        assertBad(() -> CounterPickupRules.validatePayment(35, null, -1, null, CounterPaymentMethod.CASH, null, "x", null)); // negative
    }

    @Test
    void toleratesRoundingWithinOnePesewa() {
        assertDoesNotThrow(() -> CounterPickupRules.validatePayment(35.004, 35.0, 35, null, CounterPaymentMethod.CASH, null, null, null));
    }

    private static void assertBad(org.junit.jupiter.api.function.Executable e) {
        assertEquals(400, assertThrows(ResponseStatusException.class, e).getStatusCode().value());
    }
}
