package shortly.mandmcorp.dev.shortly.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void convertsWithHalfUpRoundingAndExactSums() {
        assertEquals(1005, Money.p(10.045));   // half-up, not banker's or binary drift
        assertEquals(30, Money.p(0.1 + 0.2));  // 0.30000000000000004 -> 30 pesewas
        long sum = 0;
        for (int i = 0; i < 1000; i++) sum += Money.p(0.1);
        assertEquals(10000, sum);              // double sums would drift
        assertEquals(100.0, Money.c(sum));
        assertEquals("45.00", Money.format(4500));
        assertEquals("-0.50", Money.format(-50));
    }
}
