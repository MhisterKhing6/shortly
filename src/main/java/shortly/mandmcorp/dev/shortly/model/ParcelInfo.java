package shortly.mandmcorp.dev.shortly.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParcelInfo {
    private String parcelId;
    private String parcelDescription;
    private String receiverName;
    private String receiverPhoneNumber;
    private String receiverAddress;
    private String senderName;
    private String senderPhoneNumber;
}
