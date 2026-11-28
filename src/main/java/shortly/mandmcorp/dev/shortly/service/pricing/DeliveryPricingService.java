package shortly.mandmcorp.dev.shortly.service.pricing;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelRequest;
import shortly.mandmcorp.dev.shortly.enums.PriceOverrideReason;
import shortly.mandmcorp.dev.shortly.enums.UserRole;
import shortly.mandmcorp.dev.shortly.model.DeliveryAssignments;
import shortly.mandmcorp.dev.shortly.model.DeliveryPricingTable;
import shortly.mandmcorp.dev.shortly.model.DeliveryQuote;
import shortly.mandmcorp.dev.shortly.model.Office;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.RoutePlan;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.OfficeRepository;
import shortly.mandmcorp.dev.shortly.utils.BusinessDay;
import shortly.mandmcorp.dev.shortly.utils.CurrentUser;
import shortly.mandmcorp.dev.shortly.utils.Money;

/** Station delivery price tables, quotes, intake pricing and the pricing review (Part 5c). */
@Service
@Slf4j
public class DeliveryPricingService {

    private static final long QUOTE_VALID_MS = 30 * 60_000L;
    private static final long QUOTE_KEEP_MS = 30L * 86_400_000L;

    private final MongoTemplate mongoTemplate;
    private final OfficeRepository officeRepository;
    private final RoadDistanceService roadDistance;

    public DeliveryPricingService(MongoTemplate mongoTemplate, OfficeRepository officeRepository, RoadDistanceService roadDistance) {
        this.mongoTemplate = mongoTemplate;
        this.officeRepository = officeRepository;
        this.roadDistance = roadDistance;
    }

    /** A priced delivery: the rules' result plus where the distance came from. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Quote {
        private double distanceKm;
        /** ROAD or ESTIMATE. */
        private String distanceSource;
        private String zone;
        private List<DeliveryPricingRules.Line> lines;
        private double price;
        private int tableVersion;
        private String bucketKey;
        private String bucketLabel;
        private Double roadMinutes;
    }

    // ================================================================== tables

    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public DeliveryPricingTable ownTable() {
        return requireTable(requireOwnOffice());
    }

    @PreAuthorize("hasRole('MANAGER')")
    public DeliveryPricingTable saveOwnTable(DeliveryPricingTable table) {
        return saveTable(requireOwnOffice(), table);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public DeliveryPricingTable officeTable(String officeId) {
        requireCompanyOffice(officeId);
        return requireTable(officeId);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public DeliveryPricingTable saveOfficeTable(String officeId, DeliveryPricingTable table) {
        requireCompanyOffice(officeId);
        return saveTable(officeId, table);
    }

    /** The station's current (latest) table, or null. */
    public DeliveryPricingTable currentTable(String officeId) {
        if (officeId == null) return null;
        return mongoTemplate.findOne(new Query(Criteria.where("officeId").is(officeId))
                .with(Sort.by(Sort.Direction.DESC, "version")).limit(1), DeliveryPricingTable.class);
    }

    private DeliveryPricingTable requireTable(String officeId) {
        DeliveryPricingTable t = currentTable(officeId);
        if (t == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This station has no delivery price table yet");
        return t;
    }

    /** Every save is a new version; nothing is overwritten. */
    private DeliveryPricingTable saveTable(String officeId, DeliveryPricingTable incoming) {
        if (incoming == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A price table is required");
        DeliveryPricingRules.validate(incoming);
        User user = CurrentUser.get();
        for (int attempt = 0; attempt < 3; attempt++) {
            DeliveryPricingTable current = currentTable(officeId);
            incoming.setId(null);
            incoming.setOfficeId(officeId);
            incoming.setCompanyId(user.getCompanyId());
            incoming.setVersion(current == null ? 1 : current.getVersion() + 1);
            incoming.setUpdatedAt(System.currentTimeMillis());
            incoming.setUpdatedById(user.getUserId());
            incoming.setUpdatedByName(user.getName());
            try {
                return mongoTemplate.insert(incoming);
            } catch (DuplicateKeyException e) {
                // someone saved a version at the same moment; take the next number
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "The price table was just changed by someone else; reload and try again");
    }

    // ================================================================== quotes

    /** Staff quote from the caller's station to a pin. 404 if the station has no table. */
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public Quote staffQuote(Double latitude, Double longitude, Double weightKg, boolean fragile) {
        if (latitude == null || longitude == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude and longitude are required");
        String officeId = requireOwnOffice();
        Office office = officeRepository.findById(officeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found"));
        DeliveryPricingTable table = requireTable(officeId);
        Quote q = quote(office, table, latitude, longitude, weightKg, fragile);
        User user = CurrentUser.get();
        mongoTemplate.insert(DeliveryQuote.builder().kind("STAFF").companyId(user.getCompanyId()).officeId(officeId)
                .latitude(latitude).longitude(longitude).weightKg(weightKg).fragile(fragile)
                .distanceKm(q.getDistanceKm()).distanceSource(q.getDistanceSource()).zone(q.getZone())
                .bucketKey(q.getBucketKey()).bucketLabel(q.getBucketLabel()).tableVersion(q.getTableVersion())
                .lines(q.getLines()).deliveryFee(q.getPrice()).total(q.getPrice())
                .createdAt(System.currentTimeMillis()).validUntil(System.currentTimeMillis() + QUOTE_VALID_MS)
                .expiresAt(new Date(System.currentTimeMillis() + QUOTE_KEEP_MS)).createdById(user.getUserId()).build());
        return q;
    }

    /** Prices a delivery from the office to the pin with the given table. */
    public Quote quote(Office office, DeliveryPricingTable table, double lat, double lng, Double weightKg, boolean fragile) {
        double km;
        String source;
        Double minutes = null;
        if (office.getLatitude() != null && office.getLongitude() != null) {
            RoadDistanceService.Distance d = roadDistance.distance(office.getId(), office.getLatitude(), office.getLongitude(), lat, lng);
            km = d.km();
            source = d.source();
            minutes = d.minutes();
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Set the station's location (latitude/longitude) before pricing deliveries by distance");
        }
        DeliveryPricingRules.Price p = DeliveryPricingRules.price(table, lat, lng, km, weightKg, fragile);
        return Quote.builder().distanceKm(p.getDistanceKm()).distanceSource(source).zone(p.getZone())
                .lines(p.getLines()).price(p.getPrice()).tableVersion(p.getTableVersion())
                .bucketKey(p.getBucketKey()).bucketLabel(p.getBucketLabel()).roadMinutes(minutes).build();
    }

    // ================================================================== intake

    /**
     * Re-prices a home delivery with a pin on the server and stores the server's figures. When the
     * price charged differs from the server's suggestion (or the sent suggestion differs from it),
     * a priceOverrideReason is required; OTHER needs specialInstructions. Does nothing when the
     * station has no table or no coordinates.
     */
    public void applyIntakePricing(Parcel parcel, ParcelRequest request) {
        User user = CurrentUser.getOrNull();
        if (user != null) {
            parcel.setRegisteredById(user.getUserId());
            parcel.setRegisteredByName(user.getName());
        }
        if (!parcel.isHomeDelivery() || parcel.getReceiverLatitude() == null || parcel.getReceiverLongitude() == null) return;
        DeliveryPricingTable table = currentTable(parcel.getOfficeId());
        Office office = parcel.getOfficeId() == null ? null : officeRepository.findById(parcel.getOfficeId()).orElse(null);
        if (table == null || office == null || office.getLatitude() == null || office.getLongitude() == null) return;

        Double weight = parcel.getParcelWeight() > 0 ? parcel.getParcelWeight() : null;
        Quote q = quote(office, table, parcel.getReceiverLatitude(), parcel.getReceiverLongitude(), weight, parcel.isFragile());
        parcel.setDistanceKm(q.getDistanceKm());
        parcel.setDistanceSource(q.getDistanceSource());
        parcel.setSuggestedDeliveryCost(q.getPrice());
        parcel.setPricingTableVersion(q.getTableVersion());
        parcel.setPricingZone(q.getZone());
        parcel.setPricingBucketKey(q.getBucketKey());

        long server = Money.p(q.getPrice());
        boolean chargedDiffers = Math.abs(Money.p(parcel.getDeliveryCost()) - server) > 1;
        boolean sentDiffers = request.getSuggestedDeliveryCost() != null && Math.abs(Money.p(request.getSuggestedDeliveryCost()) - server) > 1;
        PriceOverrideReason reason = request.getPriceOverrideReason();
        if (chargedDiffers || sentDiffers) {
            if (reason == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The suggested delivery price is GHC " + Money.format(server)
                        + (chargedDiffers ? "; you charged GHC " + Money.format(Money.p(parcel.getDeliveryCost())) : "")
                        + ". Choose a reason for the difference (priceOverrideReason).");
            }
            if (reason == PriceOverrideReason.OTHER && (parcel.getSpecialInstructions() == null || parcel.getSpecialInstructions().isBlank())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explain the price in specialInstructions when the reason is OTHER");
            }
            parcel.setPriceOverrideReason(reason);
        } else {
            parcel.setPriceOverrideReason(null);
        }
    }

    // ================================================================== stage 2 review

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewBucket {
        private String key;
        private String label;
        /** ZONE, BAND or PER_KM. */
        private String kind;
        private Double upToKm;
        private int deliveries;
        private Double suggestedMedian;
        private Double chargedMedian;
        private int overrides;
        @Builder.Default
        private Map<String, Integer> overrideReasons = new LinkedHashMap<>();
        private Double avgKmDriven;
        private Double avgMinutes;
        private int quotesShown;
        private int quotesBooked;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffRow {
        private String userId;
        private String name;
        private int parcels;
        private int overrides;
        /** Average of (charged − suggested) on their overrides. */
        private Double avgOverrideDifference;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Review {
        private String officeId;
        private String officeName;
        private String from;
        private String to;
        private DeliveryPricingTable table;
        private int statsTableVersion;
        private long statsSince;
        private List<ReviewBucket> bands;
        private List<StaffRow> staff;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public Review review(String officeId, String from, String to) {
        Office office = requireCompanyOffice(officeId);
        DeliveryPricingTable table = requireTable(officeId);
        LocalDate toDay = BusinessDay.parse(to);
        LocalDate fromDay = from == null || from.isBlank() ? toDay.minusDays(56) : BusinessDay.parse(from);
        long start = BusinessDay.startMs(fromDay), end = BusinessDay.endMs(toDay);

        Map<String, ReviewBucket> buckets = new LinkedHashMap<>();
        if (table.getZones() != null) {
            for (DeliveryPricingTable.Zone z : table.getZones()) {
                buckets.put("zone:" + z.getName(), ReviewBucket.builder().key("zone:" + z.getName()).label(z.getName()).kind("ZONE").build());
            }
        }
        if ("PER_KM".equals(table.getMode())) {
            buckets.put("per_km", ReviewBucket.builder().key("per_km").label("Per km").kind("PER_KM").build());
        } else {
            double lower = 0;
            for (DeliveryPricingTable.Band b : DeliveryPricingRules.sortedBands(table)) {
                String key = "band:" + DeliveryPricingRules.fmtKm(b.getUpToKm());
                buckets.put(key, ReviewBucket.builder().key(key).kind("BAND").upToKm(b.getUpToKm())
                        .label(DeliveryPricingRules.fmtKm(lower) + "–" + DeliveryPricingRules.fmtKm(b.getUpToKm()) + " km").build());
                lower = b.getUpToKm();
            }
            buckets.put("band:over", ReviewBucket.builder().key("band:over").kind("BAND").label("Over " + DeliveryPricingRules.fmtKm(lower) + " km").build());
        }

        List<Parcel> delivered = mongoTemplate.find(new Query(Criteria.where("officeId").is(officeId)
                .and("companyId").is(office.getCompanyId()).and("homeDelivery").is(true).and("isDelivered").is(true)
                .and("createdAt").gte(start).lt(end)), Parcel.class);

        Map<String, List<Double>> suggested = new HashMap<>(), charged = new HashMap<>();
        Map<String, double[]> cost = new HashMap<>(); // km sum, minutes sum, count
        Map<String, double[]> perParcelCost = tripCostShares(delivered);
        for (Parcel p : delivered) {
            String key = bucketFor(table, p);
            if (key == null || !buckets.containsKey(key)) continue;
            ReviewBucket b = buckets.get(key);
            double[] c = perParcelCost.get(p.getParcelId());
            if (c != null) {
                double[] acc = cost.computeIfAbsent(key, k -> new double[3]);
                acc[0] += c[0];
                acc[1] += c[1];
                acc[2]++;
            }
            // Prices and overrides: current table version only.
            if (p.getPricingTableVersion() == null || p.getPricingTableVersion() != table.getVersion()) continue;
            b.setDeliveries(b.getDeliveries() + 1);
            if (p.getSuggestedDeliveryCost() != null) suggested.computeIfAbsent(key, k -> new ArrayList<>()).add(p.getSuggestedDeliveryCost());
            charged.computeIfAbsent(key, k -> new ArrayList<>()).add(p.getDeliveryCost());
            if (p.getPriceOverrideReason() != null) {
                b.setOverrides(b.getOverrides() + 1);
                b.getOverrideReasons().merge(p.getPriceOverrideReason().name(), 1, Integer::sum);
            }
        }
        for (ReviewBucket b : buckets.values()) {
            b.setSuggestedMedian(median(suggested.get(b.getKey())));
            b.setChargedMedian(median(charged.get(b.getKey())));
            double[] acc = cost.get(b.getKey());
            if (acc != null && acc[2] > 0) {
                b.setAvgKmDriven(Math.round(acc[0] / acc[2] * 10) / 10.0);
                b.setAvgMinutes((double) Math.round(acc[1] / acc[2]));
            }
        }

        // Acceptance: customer quotes on the current version, shown vs booked.
        for (DeliveryQuote q : mongoTemplate.find(new Query(Criteria.where("officeId").is(officeId).and("kind").is("CUSTOMER")
                .and("tableVersion").is(table.getVersion()).and("createdAt").gte(start).lt(end)), DeliveryQuote.class)) {
            ReviewBucket b = q.getBucketKey() == null ? null : buckets.get(q.getBucketKey());
            if (b == null) continue;
            b.setQuotesShown(b.getQuotesShown() + 1);
            if (q.getBookedRequestId() != null) b.setQuotesBooked(b.getQuotesBooked() + 1);
        }

        // Staff: priced parcels registered in the period.
        Map<String, StaffRow> staff = new LinkedHashMap<>();
        Map<String, Long> overrideDiff = new HashMap<>();
        for (Parcel p : mongoTemplate.find(new Query(Criteria.where("officeId").is(officeId).and("companyId").is(office.getCompanyId())
                .and("suggestedDeliveryCost").ne(null).and("createdAt").gte(start).lt(end)), Parcel.class)) {
            if (p.getRegisteredById() == null) continue;
            StaffRow row = staff.computeIfAbsent(p.getRegisteredById(), id -> StaffRow.builder().userId(id).name(p.getRegisteredByName()).build());
            row.setParcels(row.getParcels() + 1);
            if (p.getPriceOverrideReason() != null) {
                row.setOverrides(row.getOverrides() + 1);
                overrideDiff.merge(p.getRegisteredById(), Money.p(p.getDeliveryCost()) - Money.p(p.getSuggestedDeliveryCost()), Long::sum);
            }
        }
        staff.values().forEach(r -> {
            if (r.getOverrides() > 0) r.setAvgOverrideDifference(Money.c(Math.round((double) overrideDiff.get(r.getUserId()) / r.getOverrides())));
        });

        return Review.builder()
                .officeId(officeId).officeName(office.getName()).from(fromDay.toString()).to(toDay.toString())
                .table(table).statsTableVersion(table.getVersion()).statsSince(table.getUpdatedAt())
                .bands(new ArrayList<>(buckets.values())).staff(new ArrayList<>(staff.values()))
                .build();
    }

    /** The current table's bucket for a parcel, from its pin (zones) and stored distance. */
    private static String bucketFor(DeliveryPricingTable table, Parcel p) {
        if (p.getReceiverLatitude() == null || p.getReceiverLongitude() == null || p.getDistanceKm() == null) {
            return p.getPricingBucketKey();
        }
        return DeliveryPricingRules.price(table, p.getReceiverLatitude(), p.getReceiverLongitude(), p.getDistanceKm(), null, false).getBucketKey();
    }

    /**
     * Per-parcel trip cost from the saved route plans: each stop carries an equal share of the trip's
     * km and minutes outside the stops' own last legs, plus its own last leg. Batching makes dense
     * areas cheaper. Parcels on assignments without a plan have no cost figure.
     */
    private Map<String, double[]> tripCostShares(List<Parcel> parcels) {
        Map<String, double[]> out = new HashMap<>();
        if (parcels.isEmpty()) return out;
        List<String> ids = parcels.stream().map(Parcel::getParcelId).toList();
        for (DeliveryAssignments a : mongoTemplate.find(new Query(Criteria.where("parcels.parcelId").in(ids)
                .and("routePlan").ne(null)), DeliveryAssignments.class)) {
            RoutePlan plan = a.getRoutePlan();
            if (plan.getStopOrder() == null || plan.getStopOrder().isEmpty() || plan.getTotalKm() == null) continue;
            Map<String, RoutePlan.Leg> ownLeg = new HashMap<>();
            if (plan.getLegs() != null) for (RoutePlan.Leg l : plan.getLegs()) ownLeg.put(l.getTo(), l);
            double ownKm = 0, ownMin = 0;
            for (String stop : plan.getStopOrder()) {
                RoutePlan.Leg l = ownLeg.get(stop);
                if (l != null) {
                    ownKm += l.getKm() != null ? l.getKm() : 0;
                    ownMin += l.getMinutes() != null ? l.getMinutes() : 0;
                }
            }
            int n = plan.getStopOrder().size();
            double sharedKm = Math.max(0, plan.getTotalKm() - ownKm) / n;
            double sharedMin = Math.max(0, (plan.getTotalMinutes() != null ? plan.getTotalMinutes() : 0) - ownMin) / n;
            for (String stop : plan.getStopOrder()) {
                RoutePlan.Leg l = ownLeg.get(stop);
                double km = sharedKm + (l != null && l.getKm() != null ? l.getKm() : 0);
                double min = sharedMin + (l != null && l.getMinutes() != null ? l.getMinutes() : 0);
                out.put(stop, new double[] { km, min });
            }
        }
        return out;
    }

    private static Double median(List<Double> values) {
        if (values == null || values.isEmpty()) return null;
        List<Double> v = new ArrayList<>(values);
        v.sort(Double::compare);
        int n = v.size();
        return n % 2 == 1 ? v.get(n / 2) : Money.c(Math.round((Money.p(v.get(n / 2 - 1)) + Money.p(v.get(n / 2))) / 2.0));
    }

    // ================================================================== helpers

    private String requireOwnOffice() {
        String officeId = CurrentUser.officeId(CurrentUser.get());
        if (officeId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your account has no station");
        return officeId;
    }

    private Office requireCompanyOffice(String officeId) {
        Office office = officeId == null ? null : officeRepository.findById(officeId).orElse(null);
        User user = CurrentUser.get();
        if (office == null || (user.getRole() != UserRole.SUPERADMIN && !Objects.equals(office.getCompanyId(), user.getCompanyId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found");
        }
        return office;
    }
}
