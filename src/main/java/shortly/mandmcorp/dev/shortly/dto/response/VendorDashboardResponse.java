package shortly.mandmcorp.dev.shortly.dto.response;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VendorDashboardResponse {
    private Map<String, Long> statusSummary;
    private int totalParcels;
    private int totalStations;
    private List<VendorStationGroup> stations;
}
