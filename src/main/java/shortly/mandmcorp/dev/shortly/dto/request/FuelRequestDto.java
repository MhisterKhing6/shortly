package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FuelRequestDto {

    @NotBlank
    private String station;

    @NotBlank
    private String fuelStationNumber;

    @NotBlank
    private String attendantNumber;

    private String notes;
}
