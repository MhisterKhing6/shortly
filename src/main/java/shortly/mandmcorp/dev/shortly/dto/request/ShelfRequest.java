package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShelfRequest {
    @NotBlank(message="Name of the shelf is required")
    private String name;

    @NotBlank(message="Office Id is required")
    private String officeId;
}
