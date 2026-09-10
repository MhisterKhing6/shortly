package shortly.mandmcorp.dev.shortly.utils;

/**
 * Small helpers for phone numbers. Country codes are always prefixed with '+'
 * (E.164 convention), so a number carrying a country code must start with '+'.
 */
public final class PhoneNumberUtils {

    private PhoneNumberUtils() {
    }

    /**
     * Returns true if the phone number includes a country code (i.e. starts with '+').
     */
    public static boolean hasCountryCode(String phoneNumber) {
        return phoneNumber != null && phoneNumber.trim().startsWith("+");
    }
}
