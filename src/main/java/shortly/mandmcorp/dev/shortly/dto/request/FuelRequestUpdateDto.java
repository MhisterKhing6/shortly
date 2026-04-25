package shortly.mandmcorp.dev.shortly.dto.request;

import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.FuelRequestStatus;

@Data
public class FuelRequestUpdateDto {
    private String station;
    private String fuelStationNumber;
    private String attendantNumber;
    private String notes;
    private Double amount;
    private FuelRequestStatus status;
}
