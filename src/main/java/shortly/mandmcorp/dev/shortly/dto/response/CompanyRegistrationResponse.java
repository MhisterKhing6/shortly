package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompanyRegistrationResponse {
    private String companyId;
    private String companyName;
    private String displayName;
    private String email;
    private boolean enabled;
    private String message;
}
