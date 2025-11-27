package shortly.mandmcorp.dev.shortly.utils;

public class NotificationUtil {
    
    public static String generateOtpMessage(String otp) {
        return "Your OTP is: " + otp;
    } 

    public static String loginCredentials(String password, String phoneNumber, String name, String role) {
        return "Hello" + name + "Your login credentials on the Shortly app as a " + role + " are - Phone: " + phoneNumber + ", Password: " + password;
    } 
}
