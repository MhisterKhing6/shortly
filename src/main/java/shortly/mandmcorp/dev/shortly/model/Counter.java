package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * Atomic sequence counter used to generate human-friendly identifiers
 * (e.g. parcel barcodes). One document per sequence key, e.g. "parcel_2026".
 */
@Data
@Document(collection = "counters")
public class Counter {
    @Id
    private String id;
    private long seq;
}
