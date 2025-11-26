package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.UserRole;

@Data
public class RegisterUser {
    @NotBlank
    private String name;
    
    @NotBlank
    private String email;

    @NotBlank
    private String password;

    @NotBlank
    private String phoneNumber;

    @NotNull
    private UserRole role;
}
