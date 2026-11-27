package shortly.mandmcorp.dev.shortly.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One line of what a customer owes at the counter. Keys: inbound, storage, pickup, pod. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CounterCharge {
    private String key;
    private String label;
    private double amount;
}
