package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CompanyRegistrationRequest {

    @NotBlank(message = "Company name is required")
    private String companyName;

    @NotBlank(message = "Register user name is required")
    private String registerUserName;

    @NotBlank(message = "Work email is required")
    private String workEmail;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotBlank(message = "Password is required")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d).{7,}$",
        message = "Password must be more than 6 characters and contain both letters and numbers"
    )
    private String password;
}
