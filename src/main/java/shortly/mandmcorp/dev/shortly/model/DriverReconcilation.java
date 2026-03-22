package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;

import lombok.Data;
import java.util.List;

@Data
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
