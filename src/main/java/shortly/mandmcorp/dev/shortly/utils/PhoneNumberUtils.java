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

    /**
     * Normalises a Ghana phone number to +233XXXXXXXXX: strips spaces, dashes and brackets, turns a
     * leading 0 or 233 into +233. Other international numbers keep their + prefix. Returns null for blank.
     */
    public static String normalizeGhana(String phoneNumber) {
        if (phoneNumber == null) return null;
        String p = phoneNumber.trim().replaceAll("[\\s\\-().]", "");
        if (p.isEmpty()) return null;
        if (p.startsWith("00")) p = "+" + p.substring(2);
        if (p.startsWith("+")) return p;
        if (p.startsWith("233") && p.length() == 12) return "+" + p;
        if (p.startsWith("0") && p.length() == 10) return "+233" + p.substring(1);
        if (p.length() == 9) return "+233" + p;
        return p;
    }
}
