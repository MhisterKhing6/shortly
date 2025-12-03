package shortly.mandmcorp.dev.shortly.utils;

import java.security.SecureRandom;

public class OtpUtil {
    
    private static final SecureRandom random = new SecureRandom();
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    
    public static String generateOtp() {
        return String.format("%06d", random.nextInt(1000000));
    }
    
    public static String generateUserPassword() {
        StringBuilder password = new StringBuilder(12);
        for (int i = 0; i < 9; i++) {
            password.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return password.toString();
    }

    public static String generatVerificationCode() {
        return String.format("%06d", random.nextInt(1000000));
    }

}
