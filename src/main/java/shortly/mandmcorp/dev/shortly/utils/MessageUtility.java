package shortly.mandmcorp.dev.shortly.utils;

public class MessageUtility {
    
    public static String generateResetPasswordMessage(String baseUrl, String token, String name) {
        return "Hello, " + name + ".  use the below link to reset your password:"  + baseUrl + "/reset-password/" + token;
    }
}
