package shortly.mandmcorp.dev.shortly.service.customer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

class CustomerPortalPhoneTest {

    @Test
    void matchesTheFormatsReceiverNumbersAreStoredIn() {
        Set<String> v = CustomerPortalService.phoneVariants("024 411 8902");
        assertTrue(v.containsAll(Set.of("+233244118902", "0244118902", "233244118902")));
        assertTrue(CustomerPortalService.phoneVariants("+233244118902").contains("0244118902"));
        assertTrue(CustomerPortalService.phoneVariants(" ").isEmpty());
    }
}
