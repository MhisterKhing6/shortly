package shortly.mandmcorp.dev.shortly.dto.request;

import java.util.List;

import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.CounterPaymentMethod;

@Data
public class PodPayoutRequest {
    private List<String> collectionIds;
    private CounterPaymentMethod paymentMethod;
    /** Required for MOMO. */
    private String momoReference;
    /** Who received the money, e.g. "Kofi Mensah (vendor)". */
    private String paidTo;
    private String note;
}
