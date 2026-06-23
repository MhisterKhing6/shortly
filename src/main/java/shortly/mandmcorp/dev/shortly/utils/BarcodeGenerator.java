package shortly.mandmcorp.dev.shortly.utils;

import java.time.Year;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.model.Counter;

/**
 * Generates human-friendly, unique parcel barcodes in the form
 * {@code PARCEL-2026-000123}. The numeric suffix is backed by an atomic,
 * per-year MongoDB sequence so concurrent inserts never collide.
 */
@Component
@AllArgsConstructor
public class BarcodeGenerator {

    private static final String PREFIX = "PARCEL";

    private final MongoTemplate mongoTemplate;

    public String generate() {
        int year = Year.now().getValue();
        long seq = nextSequence(PREFIX.toLowerCase() + "_" + year);
        return String.format("%s-%d-%06d", PREFIX, year, seq);
    }

    private long nextSequence(String key) {
        Counter counter = mongoTemplate.findAndModify(
                new Query(Criteria.where("_id").is(key)),
                new Update().inc("seq", 1),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Counter.class);
        // counter is never null because upsert(true) + returnNew(true) always returns the document
        return counter.getSeq();
    }
}
