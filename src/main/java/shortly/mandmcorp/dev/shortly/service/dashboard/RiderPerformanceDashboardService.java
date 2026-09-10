package shortly.mandmcorp.dev.shortly.service.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;

import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import shortly.mandmcorp.dev.shortly.model.User;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.dto.response.RiderPerformanceDashboardResponse;
import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;
import shortly.mandmcorp.dev.shortly.model.DeliveryAssignments;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.Reconcilations;

@Service
@AllArgsConstructor
public class RiderPerformanceDashboardService {

    private static final DateTimeFormatter MONTH_IN_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter MONTH_OUT_FMT = DateTimeFormatter.ofPattern("MMM yy");

    private final MongoTemplate mongoTemplate;

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public RiderPerformanceDashboardResponse getStats(String officeId, Long startDate, Long endDate) {
        long start = startDate != null ? startDate : Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli();
        long end = endDate != null ? endDate : Instant.now().toEpochMilli();

        return RiderPerformanceDashboardResponse.builder()
                .earningsTrend(buildEarningsTrend(officeId, start, end))
                .monthlyEarnings(buildMonthlyEarnings(officeId, start, end))
                .riderLeaderboard(buildRiderLeaderboard(officeId, start, end))
                .build();
    }

    // ---- Criteria builders ----

    /** Restricts every dashboard aggregation to the logged-in admin's company. */
    private Criteria companyScope() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String companyId = (auth != null && auth.getPrincipal() instanceof User user) ? user.getCompanyId() : null;
        return Criteria.where("companyId").is(companyId);
    }

    private Criteria parcelCriteria(String officeId, long start, long end) {
        List<Criteria> conditions = new ArrayList<>();
        conditions.add(companyScope());
        conditions.add(Criteria.where("createdAt").gte(start).lte(end));
        if (officeId != null) conditions.add(Criteria.where("officeId").is(officeId));
        return new Criteria().andOperator(conditions.toArray(new Criteria[0]));
    }

    private Criteria assignmentCriteria(String officeId, long start, long end, DeliveryStatus status,
            Criteria... extras) {
        List<Criteria> conditions = new ArrayList<>();
        conditions.add(companyScope());
        conditions.add(Criteria.where("assignedAt").gte(start).lte(end));
        conditions.add(Criteria.where("status").is(status));
        if (officeId != null) conditions.add(Criteria.where("officeId").is(officeId));
        Collections.addAll(conditions, extras);
        return new Criteria().andOperator(conditions.toArray(new Criteria[0]));
    }

    // ---- Epoch-ms → date string expressions ----

    private AggregationExpression dayFromEpochMs(String field) {
        return ctx -> new Document("$dateToString",
                new Document("format", "%Y-%m-%d")
                        .append("date", new Document("$toDate", "$" + field))
                        .append("timezone", "UTC"));
    }

    private AggregationExpression monthFromEpochMs(String field) {
        return ctx -> new Document("$dateToString",
                new Document("format", "%Y-%m")
                        .append("date", new Document("$toDate", "$" + field))
                        .append("timezone", "UTC"));
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

    // ---- Station Earnings area chart (daily revenue vs collected) ----

    private List<RiderPerformanceDashboardResponse.DailyEarningsTrend> buildEarningsTrend(
            String officeId, long start, long end) {

        AggregationExpression parcelDayExpr = dayFromEpochMs("createdAt");
        Aggregation revAgg = Aggregation.newAggregation(
                Aggregation.match(parcelCriteria(officeId, start, end)),
                Aggregation.project().and(parcelDayExpr).as("day").and("deliveryCost").as("val"),
                Aggregation.group("day").sum("val").as("total"),
                Aggregation.sort(Sort.Direction.ASC, "_id"));
        Map<String, Double> revMap = new LinkedHashMap<>();
        for (Document doc : mongoTemplate.aggregate(revAgg, Parcel.class, Document.class).getMappedResults()) {
            revMap.put(doc.getString("_id"), toD(doc.get("total")));
        }

        List<Criteria> reconConds = new ArrayList<>();
        reconConds.add(companyScope());
        reconConds.add(Criteria.where("reconciledAt").gte(start).lte(end));
        reconConds.add(Criteria.where("isCompleted").is(true));
        if (officeId != null) reconConds.add(Criteria.where("officeId").is(officeId));
        Criteria reconCriteria = new Criteria().andOperator(reconConds.toArray(new Criteria[0]));

        AggregationExpression reconDayExpr = dayFromEpochMs("reconciledAt");
        Aggregation collAgg = Aggregation.newAggregation(
                Aggregation.match(reconCriteria),
                Aggregation.project().and(reconDayExpr).as("day").and("payedAmount").as("val"),
                Aggregation.group("day").sum("val").as("total"),
                Aggregation.sort(Sort.Direction.ASC, "_id"));
        Map<String, Double> collMap = new LinkedHashMap<>();
        for (Document doc : mongoTemplate.aggregate(collAgg, Reconcilations.class, Document.class).getMappedResults()) {
            collMap.put(doc.getString("_id"), toD(doc.get("total")));
        }

        return generateDates(start, end).stream()
                .map(date -> RiderPerformanceDashboardResponse.DailyEarningsTrend.builder()
                        .date(date)
                        .revenue(revMap.getOrDefault(date, 0.0))
                        .collected(collMap.getOrDefault(date, 0.0))
                        .build())
                .collect(Collectors.toList());
    }

    // ---- Monthly earnings summary (list below chart) ----

    private List<RiderPerformanceDashboardResponse.MonthlyEarningsSummary> buildMonthlyEarnings(
            String officeId, long start, long end) {

        AggregationExpression monthExpr = monthFromEpochMs("createdAt");
        Aggregation revAgg = Aggregation.newAggregation(
                Aggregation.match(parcelCriteria(officeId, start, end)),
                Aggregation.project().and(monthExpr).as("month")
                        .and("deliveryCost").as("deliveryCost"),
                Aggregation.group("month")
                        .count().as("parcels")
                        .sum("deliveryCost").as("revenue"),
                Aggregation.sort(Sort.Direction.DESC, "_id"));
        Map<String, double[]> monthRevMap = new LinkedHashMap<>();
        for (Document doc : mongoTemplate.aggregate(revAgg, Parcel.class, Document.class).getMappedResults()) {
            String m = doc.getString("_id");
            monthRevMap.put(m, new double[]{toD(doc.get("revenue")), toD(doc.get("parcels"))});
        }

        List<Criteria> reconConds = new ArrayList<>();
        reconConds.add(companyScope());
        reconConds.add(Criteria.where("createdAt").gte(start).lte(end));
        reconConds.add(Criteria.where("isCompleted").is(true));
        if (officeId != null) reconConds.add(Criteria.where("officeId").is(officeId));

        AggregationExpression reconMonthExpr = monthFromEpochMs("createdAt");
        Aggregation collAgg = Aggregation.newAggregation(
                Aggregation.match(new Criteria().andOperator(reconConds.toArray(new Criteria[0]))),
                Aggregation.project().and(reconMonthExpr).as("month").and("payedAmount").as("val"),
                Aggregation.group("month").sum("val").as("collected"));
        Map<String, Double> monthCollMap = new HashMap<>();
        for (Document doc : mongoTemplate.aggregate(collAgg, Reconcilations.class, Document.class).getMappedResults()) {
            monthCollMap.put(doc.getString("_id"), toD(doc.get("collected")));
        }

        return monthRevMap.entrySet().stream()
                .map(e -> {
                    String rawMonth = e.getKey();
                    String label = formatMonth(rawMonth);
                    double revenue = e.getValue()[0];
                    long parcels = (long) e.getValue()[1];
                    double collected = monthCollMap.getOrDefault(rawMonth, 0.0);
                    return RiderPerformanceDashboardResponse.MonthlyEarningsSummary.builder()
                            .month(label)
                            .parcels(parcels)
                            .revenue(revenue)
                            .collected(collected)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private String formatMonth(String rawMonth) {
        try {
            return YearMonth.parse(rawMonth, MONTH_IN_FMT).format(MONTH_OUT_FMT);
        } catch (Exception e) {
            return rawMonth;
        }
    }

    // ---- Rider leaderboard ----

    private List<RiderPerformanceDashboardResponse.RiderLeaderboardEntry> buildRiderLeaderboard(
            String officeId, long start, long end) {

        // Completed deliveries: deliveries count, revenue sum, avg completion time
        // $addFields keeps all existing fields and injects timeDiff so we can avg it in the group stage
        AggregationOperation addTimeDiff = ctx -> new Document("$addFields",
                new Document("timeDiff", new Document("$cond",
                        Arrays.asList(
                                new Document("$gt", Arrays.asList("$completedAt", 0L)),
                                new Document("$subtract", Arrays.asList("$completedAt", "$assignedAt")),
                                null))));

        Aggregation completedAgg = Aggregation.newAggregation(
                Aggregation.match(assignmentCriteria(officeId, start, end, DeliveryStatus.DELIVERED)),
                addTimeDiff,
                Aggregation.group("riderInfo.riderId")
                        .first("riderInfo.riderName").as("riderName")
                        .count().as("deliveries")
                        .sum("amount").as("revenue")
                        .avg("timeDiff").as("avgTimeDiffMs"),
                Aggregation.sort(Sort.Direction.DESC, "revenue"));

        Map<String, double[]> completedMap = new LinkedHashMap<>();
        // [riderName_idx=0 (stored separately), deliveries, revenue, avgTimeDiffMs]
        Map<String, String> riderNames = new HashMap<>();
        for (Document doc : mongoTemplate.aggregate(completedAgg, DeliveryAssignments.class, Document.class)
                .getMappedResults()) {
            String rid = doc.getString("_id");
            riderNames.put(rid, doc.getString("riderName"));
            completedMap.put(rid, new double[]{
                    toD(doc.get("deliveries")),
                    toD(doc.get("revenue")),
                    toD(doc.get("avgTimeDiffMs"))
            });
        }

        // Failed deliveries count per rider
        Aggregation failedAgg = Aggregation.newAggregation(
                Aggregation.match(assignmentCriteria(officeId, start, end, DeliveryStatus.RETURNED)),
                Aggregation.group("riderInfo.riderId").count().as("failed"));
        Map<String, Long> failedMap = new HashMap<>();
        for (Document doc : mongoTemplate.aggregate(failedAgg, DeliveryAssignments.class, Document.class)
                .getMappedResults()) {
            failedMap.put(doc.getString("_id"), toL(doc.get("failed")));
        }

        // Outstanding per rider (completed but not yet paid to company)
        List<Criteria> outConds = new ArrayList<>();
        outConds.add(companyScope());
        outConds.add(Criteria.where("status").is(DeliveryStatus.DELIVERED));
        outConds.add(Criteria.where("payed").is(false));
        if (officeId != null) outConds.add(Criteria.where("officeId").is(officeId));
        Aggregation outstandingAgg = Aggregation.newAggregation(
                Aggregation.match(new Criteria().andOperator(outConds.toArray(new Criteria[0]))),
                Aggregation.group("riderInfo.riderId").sum("amount").as("outstanding"));
        Map<String, Double> outstandingMap = new HashMap<>();
        for (Document doc : mongoTemplate.aggregate(outstandingAgg, DeliveryAssignments.class, Document.class)
                .getMappedResults()) {
            outstandingMap.put(doc.getString("_id"), toD(doc.get("outstanding")));
        }

        // Build leaderboard entries, sorted by revenue desc
        List<RiderPerformanceDashboardResponse.RiderLeaderboardEntry> leaderboard = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : completedMap.entrySet()) {
            String rid = entry.getKey();
            double[] stats = entry.getValue();
            long deliveries = (long) stats[0];
            double revenue = stats[1];
            double avgTimeDiffMs = stats[2];
            long failed = failedMap.getOrDefault(rid, 0L);
            double outstanding = outstandingMap.getOrDefault(rid, 0.0);
            double rating = computeRating(deliveries, failed);
            double avgTimeHours = avgTimeDiffMs > 0 ? Math.round(avgTimeDiffMs / 360_000.0) / 10.0 : 0.0;

            leaderboard.add(RiderPerformanceDashboardResponse.RiderLeaderboardEntry.builder()
                    .riderId(rid)
                    .riderName(riderNames.getOrDefault(rid, ""))
                    .deliveries(deliveries)
                    .failed(failed)
                    .revenue(revenue)
                    .outstanding(outstanding)
                    .rating(rating)
                    .avgTimeHours(avgTimeHours)
                    .build());
        }

        leaderboard.sort(Comparator.comparingDouble(
                RiderPerformanceDashboardResponse.RiderLeaderboardEntry::getRevenue).reversed());

        int rank = 1;
        for (RiderPerformanceDashboardResponse.RiderLeaderboardEntry e : leaderboard) {
            e.setRank(rank++);
        }

        return leaderboard;
    }

    // success rate scaled to 1–5 stars, rounded to 1 decimal
    private double computeRating(long deliveries, long failed) {
        long total = deliveries + failed;
        if (total == 0) return 0.0;
        double successRate = (double) deliveries / total;
        return Math.round(successRate * 50.0) / 10.0;
    }

    // ---- BSON helpers ----

    private static double toD(Object val) {
        return val instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static long toL(Object val) {
        return val instanceof Number n ? n.longValue() : 0L;
    }
}
