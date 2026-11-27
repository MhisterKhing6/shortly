package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Counter pickup money for a dashboard period, kept apart from the rider/delivery figures
 * (collected, outstanding, collectionRate are delivery-fee based and unchanged).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterDashboardTotals {
    private long pickups;
    private double due;
    private double received;
    private double cash;
    private double momo;
    /** received − due; negative = short. */
    private double shortOver;
    /** POD item values collected for senders/vendors (owed on, not revenue). */
    private double podCollected;
    /** Driver payouts in the period (net, not reversed). */
    private double driverPayouts;
}
