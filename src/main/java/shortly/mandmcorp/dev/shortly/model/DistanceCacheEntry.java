package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Cached road distance from a station to a pin rounded to about 50 m, so Google is asked once. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "distance_cache")
public class DistanceCacheEntry {
    @Id
    private String id;
    /** officeId|lat|lng with the pin rounded to ~50 m. */
    private String key;
    private double km;
    private double minutes;
    private long createdAt;
}
