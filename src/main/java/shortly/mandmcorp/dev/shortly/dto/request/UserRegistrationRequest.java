package shortly.mandmcorp.dev.shortly.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.DepartmentRole;
import shortly.mandmcorp.dev.shortly.enums.UserRole;

@Data
public class UserRegistrationRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String email;

    private String password;

    @NotBlank
    private String phoneNumber;

    @NotNull(message = "User role is required")
    private UserRole role;

    @NotNull(message = "Department role is required")
    private DepartmentRole departmentRole;

    private List<String> officeIds;
}
