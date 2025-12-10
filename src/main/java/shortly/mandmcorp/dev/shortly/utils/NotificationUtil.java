package shortly.mandmcorp.dev.shortly.utils;

public class NotificationUtil {
    
    public static String generateOtpMessage(String otp) {
        return "Your OTP is: " + otp;
    } 

    public static String loginCredentials(String password, String phoneNumber, String name, String role) {
        return "Hello " + name + "  Your login credentials on the Shortly app as a " + role + " are - Phone: " + phoneNumber + ", Password: " + password;
    } 


    public static String generateResetPasswordMessage(String baseUrl, String token, String name) {
        return "Hello, " + name + ".  use the below link to reset your password:"  + baseUrl + "/reset-password/" + token;
    }

    public static String genrateRiderAssMsg(String name, int parcelCount) {
        return "Hello " + name + ", you have been assigned " + parcelCount + " new parcels for delivery. Please check your dashboard for details.";
    }
    

    public static String generateParcelStatusUpdateMsg(String parcelCode, String status) {
        return "The status of your parcel with code " + parcelCode + " has been updated to: " + status;
    }
}
