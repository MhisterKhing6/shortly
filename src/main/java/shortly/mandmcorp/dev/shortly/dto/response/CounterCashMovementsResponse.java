package shortly.mandmcorp.dev.shortly.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import shortly.mandmcorp.dev.shortly.model.CounterCollection;
import shortly.mandmcorp.dev.shortly.model.DriverAdjustment;
import shortly.mandmcorp.dev.shortly.model.DriverPayout;

/** Everything that moved through a station's counter drawer on one day. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterCashMovementsResponse {
    private String date;
    private String officeId;
    private boolean closed;
    private List<CounterCollection> collections;
    private List<DriverPayout> driverCashPayouts;
    private List<DriverAdjustment> driverCashAdvances;
    /** POD values paid on to senders in cash this day. */
    private List<CounterCollection> podCashPayouts;
    private Totals totals;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Totals {
        private int count;
        private double due;
        private double received;
        /** Cash taken at the counter. */
        private double counterCash;
        private double momo;
        /** received − due: negative = short. */
        private double shortOver;
        private double driverCashPayouts;
        private double driverCashAdvances;
        private double podCashPayouts;
        /** counterCash − driver cash payouts − driver cash advances − POD cash paid out: what should be in the drawer. */
        private double expectedCash;
        private double expectedMomo;
    }
}
