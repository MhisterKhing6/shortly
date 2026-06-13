package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorStationEarnings {
    private String officeId;
    private String officeName;
    private int parcelCount;
    private int deliveredCount;
    private double collectedAmount;
    private double totalAmount;
}
