package shortly.mandmcorp.dev.shortly.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A closed counter day for one station. Closed days are never edited; corrections are adjustments. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "counter_cashups")
@CompoundIndex(name = "office_date_unique", def = "{'officeId': 1, 'date': 1}", unique = true)
public class CounterCashup {
    @Id
    private String id;
    private String companyId;
    private String officeId;
    /** yyyy-MM-dd, station-local. */
    private String date;

    /** Server figure: counter cash taken − driver cash payouts − driver cash advances. */
    private double expectedCash;
    private double countedCash;
    private double cashDifference;
    /** Server figure: MoMo taken at the counter. */
    private double expectedMomo;
    private String note;

    // Breakdown at closing time
    private int collectionCount;
    private double totalDue;
    private double totalReceived;
    private double counterCash;
    private double driverCashPayouts;
    private double driverCashAdvances;
    private double shortOver;

    private long closedAt;
    private String closedById;
    private String closedByName;

    @Builder.Default
    private List<Adjustment> adjustments = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Adjustment {
        private double amount;
        private String reason;
        private long createdAt;
        private String createdById;
        private String createdByName;
    }
}
