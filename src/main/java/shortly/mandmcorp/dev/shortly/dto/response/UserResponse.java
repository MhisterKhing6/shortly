package shortly.mandmcorp.dev.shortly.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class UserResponse {
    private String message;
    private String id;
    
}
