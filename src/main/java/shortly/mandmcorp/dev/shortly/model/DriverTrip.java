package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One arrival of a driver at a station: what they declared against what was received. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "driver_trips")
@CompoundIndex(name = "driver_day_idx", def = "{'driverId': 1, 'businessDate': 1, 'vehicleNumber': 1}")
public class DriverTrip {
    @Id
    private String id;
    private String companyId;
    private String officeId;
    private String driverId;
    private String driverName;
    private String vehicleNumber;
    private String origin;
    private long arrivedAt;
    private String businessDate;
    private String receivedById;
    private String receivedByName;
    /** Parcel count the driver declared; null if not given. */
    private Integer declaredCount;
    /** Parcels actually registered against this trip. */
    private int receivedCount;
    private String note;
}
