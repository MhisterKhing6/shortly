package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import shortly.mandmcorp.dev.shortly.enums.FuelRequestStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "fuel_requests")
public class FuelRequest {

    @Id
    private String id;

    private FuelRequestStatus status = FuelRequestStatus.PENDING;

    private RiderInfo riderInfo;

    private String officeId;

    private String station;

    private String notes;

    private Double amount;

    private String fuleStationPhoneNumber;

    private String attendantPhoneNumber;

    @CreatedDate
    private Long createdAt;

    @LastModifiedDate
    private Long updatedAt;
}
