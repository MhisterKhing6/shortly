package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import java.util.List;

@Data
@Document(collection = "driver_reconcilations")
@CompoundIndex(name = "parcel_id_idx", def = "{'parcels.parcelId': 1}")
public class DriverReconcilation {
    
    @Id
    private String id;
    private List<ParcelInfo> parcels;
    private  boolean payed;
    private String riderName;
    private String riderPhoneNumber;
    private double totalAmount;
    private double amountDelivered;
    private String officeId;
    
    
}
