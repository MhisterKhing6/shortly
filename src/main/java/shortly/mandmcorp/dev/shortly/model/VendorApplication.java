package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A public "become a partner" request from the landing page. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "vendor_applications")
public class VendorApplication {
    public static final String NEW = "NEW";

    @Id
    private String id;
    /** Company whose site the form was sent from; null if it couldn't be told. */
    private String companyId;
    private String businessName;
    private String contactName;
    private String phoneNumber;
    private String email;
    private String area;
    private Integer parcelsPerWeek;
    private String message;
    /** NEW, CONTACTED, APPROVED or REJECTED. */
    private String status;
    private String adminNote;
    private String handledById;
    private String handledByName;
    private Long handledAt;
    /** Vendor user created on approval, if any. */
    private String vendorUserId;
    private String sourceHost;
    private long createdAt;
}
