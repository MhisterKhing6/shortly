package shortly.mandmcorp.dev.shortly.config;

import java.time.Duration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates the indexes the newer features rely on (unique keys that stop double-recording, TTLs,
 * and indexes for heavy driver-payment queries). Index auto-creation is off in this app, so they
 * are created explicitly here. A failure is logged, never fatal, so startup can't be blocked by
 * old data.
 */
@Component
@Slf4j
public class IndexInitializer implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    public IndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Counter cash-up
        ensure("counter_collections", new Index().on("parcelId", Sort.Direction.ASC).unique().named("parcel_unique"));
        ensure("counter_collections", compound("office_day_idx", "companyId", "officeId", "businessDate", "pickedUpAt"));
        ensure("counter_cashups", compound("office_date_unique", "officeId", "date").unique());
        // Driver payments
        ensure("driverAssignment", compound("driver_state_idx", "driverId", "state"));
        ensure("driverAssignment", compound("office_state_idx", "officeId", "state"));
        ensure("driverAssignment", new Index().on("payoutId", Sort.Direction.ASC).named("payout_idx"));
        ensure("driverAssignment", new Index().on("parcelId", Sort.Direction.ASC).named("parcel_idx"));
        ensure("driverAssignment", new Index().on("tripId", Sort.Direction.ASC).named("trip_idx"));
        ensure("driver_trips", compound("driver_arrived_idx", "driverId", "arrivedAt"));
        ensure("driver_payouts", compound("office_paid_idx", "officeId", "paidAt"));
        ensure("driver_payouts", compound("driver_paid_idx", "driverId", "paidAt"));
        ensure("driver_adjustments", compound("driver_open_idx", "driverId", "open"));
        ensure("drivers", compound("company_phone_idx", "companyId", "phoneNumbers"));
        // Assignments: one active assignment per parcel
        ensure("parcelss", new Index().on("activeAssignmentId", Sort.Direction.ASC).sparse().named("active_assignment_idx"));
        // Idempotency keys and customer access expire on their own
        ensure("idempotency_keys", new Index().on("createdAt", Sort.Direction.ASC).expire(Duration.ofHours(24)).named("ttl_24h"));
        ensure("customer_access", new Index().on("tokenHash", Sort.Direction.ASC).named("token_idx"));
        ensure("customer_access", new Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO).named("ttl_expires"));
        ensure("customer_access", compound("phone_kind_idx", "phoneNumber", "kind", "createdAt"));
        // Delivery pricing, quotes, requests, vendor
        ensure("delivery_pricing_tables", compound("office_version_unique", "officeId", "version").unique());
        ensure("delivery_quotes", new Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ofDays(30)).named("ttl_30d"));
        ensure("distance_cache", new Index().on("key", Sort.Direction.ASC).unique().named("key_unique"));
        ensure("customer_delivery_requests", new Index().on("paystackReference", Sort.Direction.ASC).unique().sparse().named("reference_unique"));
        ensure("vendor_applications", compound("phone_created_idx", "phoneNumber", "createdAt"));
    }

    private static Index compound(String name, String... fields) {
        org.bson.Document keys = new org.bson.Document();
        for (String f : fields) keys.append(f, 1);
        return new CompoundIndexDefinition(keys).named(name);
    }

    private void ensure(String collection, IndexDefinition index) {
        try {
            mongoTemplate.indexOps(collection).createIndex(index);
        } catch (RuntimeException e) {
            log.warn("Could not create index {} on {}: {}", index.getIndexOptions().get("name"), collection, e.getMessage());
        }
    }
}
