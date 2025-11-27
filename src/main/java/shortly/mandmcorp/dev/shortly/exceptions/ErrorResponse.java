package shortly.mandmcorp.dev.shortly.exceptions;

import java.util.HashMap;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErrorResponse {
    private String message;
    HashMap<String, Object> data;
    
}
