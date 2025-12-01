package shortly.mandmcorp.dev.shortly.exceptions;

public class WrongCredentialsException extends  RuntimeException {
    
    public WrongCredentialsException(String message) {
        super(message);
    }
}
