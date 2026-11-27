package shortly.mandmcorp.dev.shortly.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.DepartmentRole;
import shortly.mandmcorp.dev.shortly.enums.UserRole;

@Data
@Builder
public class UserRegistrationResponse {
    private String name;
    private String phoneNumber;
    private String email;
    private String userId;
    private UserRole role;
    private DepartmentRole departmentRole;
    private String companyName;
    private List<String> officeNames;
}
