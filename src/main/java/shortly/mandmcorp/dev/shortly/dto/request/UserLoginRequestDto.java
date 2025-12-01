package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserLoginRequestDto {
    @NotNull(message = "Phone number is required")
    @Pattern(regexp = "^\\+233[2-9][0-9]{8}$", message = "Phone number must start with +233 and be valid Ghana format")
    private String phoneNumber;
    
    @NotNull(message = "Password is required")
    private String password;
}
