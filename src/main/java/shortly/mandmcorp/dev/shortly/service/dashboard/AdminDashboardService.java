package shortly.mandmcorp.dev.shortly.service.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.dto.response.DashboardStatsResponse;
import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;
import shortly.mandmcorp.dev.shortly.enums.RiderStatus;
import shortly.mandmcorp.dev.shortly.enums.UserRole;
import shortly.mandmcorp.dev.shortly.model.DeliveryAssignments;
import shortly.mandmcorp.dev.shortly.model.DriverReconcilation;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.Reconcilations;
import shortly.mandmcorp.dev.shortly.model.RiderStatusModel;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.OfficeRepository;
import shortly.mandmcorp.dev.shortly.repository.UserRepository;

@Service
@AllArgsConstructor
public class AdminDashboardService {

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final OfficeRepository officeRepository;

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public DashboardStatsResponse getStats(String officeId, Long startDate, Long endDate) {
        long start = startDate != null ? startDate : Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli();
        long end = endDate != null ? endDate : Instant.now().toEpochMilli();

        // Overview
        double totalRevenue = sumField("deliveryCost", parcelCriteria(officeId, start, end), Parcel.class);
        long totalParcels = countByCriteria(parcelCriteria(officeId, start, end), Parcel.class);
        long deliveredParcels = countByCriteria(
                parcelCriteria(officeId, start, end, Criteria.where("isDelivered").is(true)), Parcel.class);
        double successRate = totalParcels > 0
                ? Math.round((double) deliveredParcels / totalParcels * 1000.0) / 10.0 : 0.0;
        long activeRiders = countActiveRiders(officeId);

        // Revenue breakdown
        double collected = sumField("amountPayed", reconcilCriteria(officeId, start, end, true), Reconcilations.class);
        double outstanding = Math.max(totalRevenue - collected, 0.0);
        double driverPaymentsOwed = sumField("totalAmount", driverReconCriteria(officeId), DriverReconcilation.class);

        // Daily trends
        Map<String, Double> dailyRevMap = dailySumMap(
                "deliveryCost", "createdAt", parcelCriteria(officeId, start, end), Parcel.class);
        Map<String, Double> dailyCollMap = dailySumMap(
                "amountPayed", "reconciledAt", reconcilCollectedDailyCriteria(officeId, start, end), Reconcilations.class);
        List<DashboardStatsResponse.DailyRevenueTrend> revenueVsCollected =
                buildRevenueTrend(dailyRevMap, dailyCollMap, start, end);

        Map<String, Long> dailyDelivered = dailyCountMap(
                "updatedAt", assignmentCriteria(officeId, start, end, DeliveryStatus.DELIVERED), DeliveryAssignments.class);
        Map<String, Long> dailyFailed = dailyCountMap(
                "updatedAt", assignmentCriteria(officeId, start, end, DeliveryStatus.RETURNED), DeliveryAssignments.class);
        List<DashboardStatsResponse.DailyDeliveryTrend> deliveredVsFailed =
                buildDeliveryTrend(dailyDelivered, dailyFailed, start, end);

        return DashboardStatsResponse.builder()
                .totalRevenue(totalRevenue)
                .totalParcels(totalParcels)
                .successRate(successRate)
                .activeRiders(activeRiders)
                .collected(collected)
                .outstanding(outstanding)
                .driverPaymentsOwed(driverPaymentsOwed)
                .revenueVsCollected(revenueVsCollected)
                .deliveredVsFailed(deliveredVsFailed)
                .parcelPipeline(buildPipeline(officeId, start, end))
                .stationSnapshot(buildStationSnapshot(officeId, start, end))
                .topRiders(buildTopRiders(officeId, start, end))
                .build();
    }

    // ---- Criteria builders ----

    /** Restricts every dashboard aggregation to the logged-in admin's company. */
    private Criteria companyScope() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String companyId = (auth != null && auth.getPrincipal() instanceof User user) ? user.getCompanyId() : null;
        return Criteria.where("companyId").is(companyId);
    }

    private String loggedInCompanyId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof User user) ? user.getCompanyId() : null;
    }

    private Criteria parcelCriteria(String officeId, long start, long end, Criteria... extras) {
        List<Criteria> conditions = new ArrayList<>();
        conditions.add(companyScope());
        conditions.add(Criteria.where("createdAt").gte(start).lte(end));
        if (officeId != null) conditions.add(Criteria.where("officeId").is(officeId));
        Collections.addAll(conditions, extras);
        return new Criteria().andOperator(conditions.toArray(new Criteria[0]));
    }

    private Criteria reconcilCriteria(String officeId, long start, long end, boolean completed) {
        List<Criteria> conditions = new ArrayList<>();
        conditions.add(companyScope());
        conditions.add(Criteria.where("createdAt").gte(start).lte(end));
        conditions.add(Criteria.where("isCompleted").is(completed));
        if (officeId != null) conditions.add(Criteria.where("officeId").is(officeId));
        return new Criteria().andOperator(conditions.toArray(new Criteria[0]));
    }

    private Criteria reconcilCollectedDailyCriteria(String officeId, long start, long end) {
        List<Criteria> conditions = new ArrayList<>();
        conditions.add(companyScope());
        conditions.add(Criteria.where("reconciledAt").gte(start).lte(end));
        conditions.add(Criteria.where("isCompleted").is(true));
        if (officeId != null) conditions.add(Criteria.where("officeId").is(officeId));
        return new Criteria().andOperator(conditions.toArray(new Criteria[0]));
    }

    private Criteria driverReconCriteria(String officeId) {
        List<Criteria> conditions = new ArrayList<>();
        conditions.add(companyScope());
        conditions.add(Criteria.where("payed").is(false));
        if (officeId != null) conditions.add(Criteria.where("officeId").is(officeId));
        return new Criteria().andOperator(conditions.toArray(new Criteria[0]));
    }

    private Criteria assignmentCriteria(String officeId, long start, long end, DeliveryStatus status) {
        List<Criteria> conditions = new ArrayList<>();
        conditions.add(companyScope());
        conditions.add(Criteria.where("assignedAt").gte(start).lte(end));
        conditions.add(Criteria.where("status").is(status));
        if (officeId != null) conditions.add(Criteria.where("officeId").is(officeId));
        return new Criteria().andOperator(conditions.toArray(new Criteria[0]));
    }

    // ---- Generic aggregation helpers ----

    private double sumField(String field, Criteria criteria, Class<?> entityClass) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group().sum(field).as("total"));
        Document result = mongoTemplate.aggregate(agg, entityClass, Document.class).getUniqueMappedResult();
        return result != null ? toD(result.get("total")) : 0.0;
    }

    private long countByCriteria(Criteria criteria, Class<?> entityClass) {
        return mongoTemplate.count(new Query(criteria), entityClass);
    }

    private Map<String, Double> dailySumMap(String sumField, String dateField, Criteria criteria, Class<?> entityClass) {
        AggregationExpression dayExpr = dayFromEpochMs(dateField);
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.project().and(dayExpr).as("day").and(sumField).as("val"),
                Aggregation.group("day").sum("val").as("total"),
                Aggregation.sort(Sort.Direction.ASC, "_id"));
        List<Document> docs = mongoTemplate.aggregate(agg, entityClass, Document.class).getMappedResults();
        Map<String, Double> result = new LinkedHashMap<>();
        for (Document doc : docs) {
            result.put(doc.getString("_id"), toD(doc.get("total")));
        }
        return result;
    }

    private Map<String, Long> dailyCountMap(String dateField, Criteria criteria, Class<?> entityClass) {
        AggregationExpression dayExpr = dayFromEpochMs(dateField);
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.project().and(dayExpr).as("day"),
                Aggregation.group("day").count().as("total"),
                Aggregation.sort(Sort.Direction.ASC, "_id"));
        List<Document> docs = mongoTemplate.aggregate(agg, entityClass, Document.class).getMappedResults();
        Map<String, Long> result = new LinkedHashMap<>();
        for (Document doc : docs) {
            result.put(doc.getString("_id"), toL(doc.get("total")));
        }
        return result;
    }

    /** Converts an epoch-millisecond field to a UTC calendar-day string (yyyy-MM-dd). */
    private AggregationExpression dayFromEpochMs(String field) {
        return ctx -> new Document("$dateToString",
                new Document("format", "%Y-%m-%d")
                        .append("date", new Document("$toDate", "$" + field))
                        .append("timezone", "UTC"));
    }

    // ---- Trend builders ----

    private List<DashboardStatsResponse.DailyRevenueTrend> buildRevenueTrend(
            Map<String, Double> revMap, Map<String, Double> collMap, long startMs, long endMs) {
        return generateDates(startMs, endMs).stream()
                .map(date -> DashboardStatsResponse.DailyRevenueTrend.builder()
                        .date(date)
                        .revenue(revMap.getOrDefault(date, 0.0))
                        .collected(collMap.getOrDefault(date, 0.0))
                        .build())
                .collect(Collectors.toList());
    }

    private List<DashboardStatsResponse.DailyDeliveryTrend> buildDeliveryTrend(
            Map<String, Long> deliveredMap, Map<String, Long> failedMap, long startMs, long endMs) {
        return generateDates(startMs, endMs).stream()
                .map(date -> DashboardStatsResponse.DailyDeliveryTrend.builder()
                        .date(date)
                        .delivered(deliveredMap.getOrDefault(date, 0L))
                        .failed(failedMap.getOrDefault(date, 0L))
                        .build())
                .collect(Collectors.toList());
    }

    private List<String> generateDates(long startMs, long endMs) {
        List<String> dates = new ArrayList<>();
        LocalDate start = LocalDate.ofEpochDay(startMs / 86_400_000L);
        LocalDate end = LocalDate.ofEpochDay(endMs / 86_400_000L);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dates.add(d.toString());
        }
        return dates;
    }

    // ---- Active riders ----

    private long countActiveRiders(String officeId) {
        Query activeQuery = new Query(Criteria.where("riderStatus").ne(RiderStatus.OFFLINE));
        List<RiderStatusModel> activeStatuses = mongoTemplate.find(activeQuery, RiderStatusModel.class);

        // No office filter: still scope to the logged-in admin's company.
        if (officeId == null) {
            String companyId = loggedInCompanyId();
            return activeStatuses.stream()
                    .filter(s -> s.getRider() != null
                            && java.util.Objects.equals(s.getRider().getCompanyId(), companyId))
                    .count();
        }

        List<User> officeRiders = userRepository.findByRoleAndOfficeIdsContaining(UserRole.RIDER, officeId);
        Set<String> riderIds = officeRiders.stream().map(User::getUserId).collect(Collectors.toSet());

        return activeStatuses.stream()
                .filter(s -> s.getRider() != null && riderIds.contains(s.getRider().getUserId()))
                .count();
    }

    // ---- Parcel pipeline ----

    private DashboardStatsResponse.ParcelPipeline buildPipeline(String officeId, long start, long end) {
        long registered = countByCriteria(parcelCriteria(officeId, start, end), Parcel.class);
        long contacted = countByCriteria(
                parcelCriteria(officeId, start, end, Criteria.where("hasCalled").is(true)), Parcel.class);
        long readyForDelivery = countByCriteria(
                parcelCriteria(officeId, start, end,
                        Criteria.where("isParcelAssigned").is(false),
                        Criteria.where("isDelivered").is(false)), Parcel.class);
        long assigned = countByCriteria(
                parcelCriteria(officeId, start, end,
                        Criteria.where("isParcelAssigned").is(true),
                        Criteria.where("pickedUp").is(false),
                        Criteria.where("isDelivered").is(false)), Parcel.class);
        long outForDelivery = countByCriteria(
                parcelCriteria(officeId, start, end,
                        Criteria.where("pickedUp").is(true),
                        Criteria.where("isDelivered").is(false)), Parcel.class);
        long delivered = countByCriteria(
                parcelCriteria(officeId, start, end, Criteria.where("isDelivered").is(true)), Parcel.class);
        long failed = countByCriteria(
                parcelCriteria(officeId, start, end, Criteria.where("returnCount").gt(0)), Parcel.class);

        return DashboardStatsResponse.ParcelPipeline.builder()
                .registered(registered)
                .contacted(contacted)
                .readyForDelivery(readyForDelivery)
                .assigned(assigned)
                .outForDelivery(outForDelivery)
                .delivered(delivered)
                .failed(failed)
                .build();
    }

    // ---- Station snapshot ----

    private List<DashboardStatsResponse.StationStats> buildStationSnapshot(String officeId, long start, long end) {
        AggregationExpression deliveredFlag = ctx -> new Document("$cond",
                Arrays.asList(new Document("$eq", Arrays.asList("$isDelivered", true)), 1, 0));

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(parcelCriteria(officeId, start, end)),
                Aggregation.group("officeId")
                        .count().as("parcels")
                        .sum("deliveryCost").as("revenue")
                        .sum(deliveredFlag).as("deliveredCount"));

        List<Document> docs = mongoTemplate.aggregate(agg, Parcel.class, Document.class).getMappedResults();

        Map<String, String> officeNames = officeRepository.findAll().stream()
                .collect(Collectors.toMap(o -> o.getId(), o -> o.getName()));

        return docs.stream()
                .map(doc -> {
                    String oid = doc.getString("_id");
                    long parcels = toL(doc.get("parcels"));
                    double revenue = toD(doc.get("revenue"));
                    long deliveredCount = toL(doc.get("deliveredCount"));
                    double rate = parcels > 0
                            ? Math.round((double) deliveredCount / parcels * 1000.0) / 10.0 : 0.0;
                    return DashboardStatsResponse.StationStats.builder()
                            .officeId(oid)
                            .stationName(officeNames.getOrDefault(oid, oid))
                            .parcels(parcels)
                            .revenue(revenue)
                            .successRate(rate)
                            .build();
                })
                .sorted(Comparator.comparingDouble(DashboardStatsResponse.StationStats::getRevenue).reversed())
                .collect(Collectors.toList());
    }

    // ---- Top riders ----

    private List<DashboardStatsResponse.TopRider> buildTopRiders(String officeId, long start, long end) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(assignmentCriteria(officeId, start, end, DeliveryStatus.DELIVERED)),
                Aggregation.group("riderInfo.riderId")
                        .first("riderInfo.riderName").as("riderName")
                        .sum("amount").as("revenue")
                        .count().as("deliveries"),
                Aggregation.sort(Sort.Direction.DESC, "revenue"),
                Aggregation.limit(10));

        List<Document> docs = mongoTemplate.aggregate(agg, DeliveryAssignments.class, Document.class)
                .getMappedResults();

        return docs.stream()
                .map(doc -> DashboardStatsResponse.TopRider.builder()
                        .riderId(doc.getString("_id"))
                        .riderName(doc.getString("riderName"))
                        .deliveries(toL(doc.get("deliveries")))
                        .revenue(toD(doc.get("revenue")))
                        .build())
                .collect(Collectors.toList());
    }

    // ---- BSON number helpers ----

    private static double toD(Object val) {
        return val instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static long toL(Object val) {
        return val instanceof Number n ? n.longValue() : 0L;
    }
}
