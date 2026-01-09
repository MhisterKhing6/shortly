package shortly.mandmcorp.dev.shortly.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiderInfo {
    private String riderId;
    private String riderName;
    private String riderPhoneNumber;
}
