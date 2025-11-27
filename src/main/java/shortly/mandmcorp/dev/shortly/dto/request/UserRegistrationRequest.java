package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.UserRole;

@Data
public class UserRegistrationRequest {
    @NotBlank
    private String name;
    
    @NotBlank
    private String email;

    private String password;

    @NotBlank
    @Pattern(regexp = "^(\\+233|0)[2-9][0-9]{8}$", message = "Phone number must be a valid Ghana phone number")
    private String phoneNumber;

    @NotNull
    private UserRole role;
}
