package shortly.mandmcorp.dev.shortly.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import shortly.mandmcorp.dev.shortly.enums.DriverStatus;
import shortly.mandmcorp.dev.shortly.enums.MomoNetwork;

/** A driver who brings parcels in. Phone numbers are stored normalised (+233...). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "drivers")
public class Driver {
    @Id
    private String id;
    @Indexed
    private String companyId;
    @Builder.Default
    private List<String> officeIds = new ArrayList<>();
    private String name;
    @Indexed
    @Builder.Default
    private List<String> phoneNumbers = new ArrayList<>();
    private String momoNumber;
    private MomoNetwork momoNetwork;
    @Builder.Default
    private List<String> vehicles = new ArrayList<>();
    /** Transport company the driver works for, if any. */
    private String company;
    @Builder.Default
    private DriverStatus status = DriverStatus.ACTIVE;
    @CreatedDate
    private Long createdAt;
    @LastModifiedDate
    private Long updatedAt;
}
