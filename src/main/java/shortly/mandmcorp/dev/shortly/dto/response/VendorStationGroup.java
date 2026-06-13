package shortly.mandmcorp.dev.shortly.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VendorStationGroup {
    private String officeId;
    private String officeName;
    private int parcelCount;
    private int podCount;
    private double totalAmount;
    private List<VendorParcelItem> parcels;
}
