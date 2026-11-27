package shortly.mandmcorp.dev.shortly.model;


import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "offices")
public class Office {

    @Id
    private String id;

    @Indexed
    private String name;

    @Indexed
    private String companyId;

    @Indexed(unique = true)
    private String code;

    private String address;
    
    private String phoneNumber;

    /** Station coordinates (WGS84). Used as the start/end anchor for route and off-task checks. */
    private Double latitude;
    private Double longitude;

    /** null = not configured; true = charge pickupChargeAmount by default; false = no charge. */
    private Boolean pickupChargeEnabled;
    /** GHC. Positive when enabled, null when disabled. */
    private Double pickupChargeAmount;
    
    @DBRef
    private Location location;

    @DBRef
    private User manager;

    @CreatedDate
    private Long createdAt;
    
    @LastModifiedDate
    private Long updatedAt;
}
