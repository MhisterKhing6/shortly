package shortly.mandmcorp.dev.shortly.service.driverpay;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import shortly.mandmcorp.dev.shortly.utils.BusinessDay;

class DriverPaymentRangeTest {

    @Test
    void acceptsDaysAndEpochMillis() {
        long[] days = DriverPaymentService.range("2026-11-24", "2026-11-27", 30);
        assertEquals(BusinessDay.startMs(LocalDate.of(2026, 11, 24)), days[0]);
        assertEquals(BusinessDay.endMs(LocalDate.of(2026, 11, 27)), days[1]);

        long[] ms = DriverPaymentService.range("1795474313000", "1795560713000", 30);
        assertEquals(1795474313000L, ms[0]);
        assertEquals(1795560713001L, ms[1]);
    }

    @Test
    void defaultsToTheLastNDaysAndRejectsBackwardsRanges() {
        long[] d = DriverPaymentService.range(null, null, 30);
        assertEquals(30 * 86_400_000L, d[1] - d[0]);
        assertThrows(ResponseStatusException.class, () -> DriverPaymentService.range("2026-11-27", "2026-11-20", 30));
    }
}
