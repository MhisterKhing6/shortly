package shortly.mandmcorp.dev.shortly.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VendorEarningsResponse {
    private double totalEarnable;
    private double amountReady;
    private double pendingPayout;
    private int failedDeliveriesCount;
    private double collectionRate;
    private List<VendorStationEarnings> earningsByStation;
    private List<VendorCollectedParcel> collectedParcels;
}
