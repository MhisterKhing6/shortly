package shortly.mandmcorp.dev.shortly.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PhoneNumberUtilsTest {

    @Test
    void normalisesGhanaNumbers() {
        assertEquals("+233244118902", PhoneNumberUtils.normalizeGhana("0244118902"));
        assertEquals("+233244118902", PhoneNumberUtils.normalizeGhana("024 411 8902"));
        assertEquals("+233244118902", PhoneNumberUtils.normalizeGhana("233244118902"));
        assertEquals("+233244118902", PhoneNumberUtils.normalizeGhana("+233 24-411-8902"));
        assertEquals("+233244118902", PhoneNumberUtils.normalizeGhana("00233244118902"));
        assertEquals("+233244118902", PhoneNumberUtils.normalizeGhana("244118902"));
        assertEquals("+447700900123", PhoneNumberUtils.normalizeGhana("+44 7700 900123"));
        assertNull(PhoneNumberUtils.normalizeGhana("  "));
        assertNull(PhoneNumberUtils.normalizeGhana(null));
    }
}
