package shortly.mandmcorp.dev.shortly.exceptions;

import java.util.HashMap;

import lombok.Data;

@Data
public class ErrorResponse {
    private int status;
    private String message;
    HashMap<String, Object> data;
    
}
