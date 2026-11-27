package shortly.mandmcorp.dev.shortly.service.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import shortly.mandmcorp.dev.shortly.model.User;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.dto.response.RevenueDashboardResponse;
import shortly.mandmcorp.dev.shortly.model.DriverReconcilation;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.Reconcilations;
import shortly.mandmcorp.dev.shortly.repository.OfficeRepository;

@Service
@AllArgsConstructor
public class RevenueDashboardService {

    private final MongoTemplate mongoTemplate;
    private final OfficeRepository officeRepository;

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public RevenueDashboardResponse getRevenueStats(String officeId, Long startDate, Long endDate) {
        long start = startDate != null ? startDate : Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli();
        long end = endDate != null ? endDate : Instant.now().toEpochMilli();

        long totalParcels = mongoTemplate.count(new Query(parcelCriteria(officeId, start, end)), Parcel.class);
        double totalRevenue = sumField("deliveryCost", parcelCriteria(officeId, start, end), Parcel.class);
        double collected = sumField("payedAmount", reconcilCriteria(officeId, start, end, true), Reconcilations.class);
        double outstanding = Math.max(totalRevenue - collected, 0.0);
        double collectionRate = totalRevenue > 0 ? Math.round(collected / totalRevenue * 10000.0) / 100.0 : 0.0;
        double avgPerParcel = totalParcels > 0 ? totalRevenue / totalParcels : 0.0;
        double driverPayments = sumField("totalAmount", driverReconCriteria(officeId), DriverReconcilation.class);

        return RevenueDashboardResponse.builder()
                .totalRevenue(totalRevenue)
                .collected(collected)
                .outstanding(outstanding)
                .collectionRate(collectionRate)
                .avgPerParcel(avgPerParcel)
                .driverPayments(driverPayments)
                .revenueVsCollectedTrend(buildDailyRevenueTrend(officeId, start, end))
                .revenueByStation(buildRevenueByStation(officeId, start, end))
                .paymentMethodTrend(buildPaymentMethodTrend(officeId, start, end))
                .revenueByType(buildRevenueByType(officeId, start, end))
                .paymentMethods(buildPaymentMethodTotals(officeId, start, end))
                .revenueByDayOfWeek(buildRevenueByDayOfWeek(officeId, start, end))
                .build();
    }

    // ---- Criteria builders ----

    /** Restricts every dashboard aggregation to the logged-in admin's company. */
    private Criteria companyScope() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String companyId = (auth != null && auth.getPrincipal() instanceof User user) ? user.getCompanyId() : null;
        return Criteria.where("companyId").is(companyId);
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

    private Criteria driverReconCriteria(String officeId) {
        List<Criteria> conditions = new ArrayList<>();
        conditions.add(companyScope());
        conditions.add(Criteria.where("payed").is(false));
        if (officeId != null) conditions.add(Criteria.where("officeId").is(officeId));
        return new Criteria().andOperator(conditions.toArray(new Criteria[0]));
    }

    // ---- Generic helpers ----

    private double sumField(String field, Criteria criteria, Class<?> entityClass) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group().sum(field).as("total"));
        Document result = mongoTemplate.aggregate(agg, entityClass, Document.class).getUniqueMappedResult();
        return result != null ? toD(result.get("total")) : 0.0;
    }

    private AggregationExpression dayFromEpochMs(String field) {
        return ctx -> new Document("$dateToString",
                new Document("format", "%Y-%m-%d")
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

    // ---- Daily revenue trend (revenue, collected, outstanding per day) ----

    private List<RevenueDashboardResponse.DailyRevenueTrend> buildDailyRevenueTrend(
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
                .map(date -> {
                    double rev = revMap.getOrDefault(date, 0.0);
                    double coll = collMap.getOrDefault(date, 0.0);
                    return RevenueDashboardResponse.DailyRevenueTrend.builder()
                            .date(date)
                            .revenue(rev)
                            .collected(coll)
                            .outstanding(Math.max(rev - coll, 0.0))
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ---- Revenue by station (with collected / outstanding per station) ----

    private List<RevenueDashboardResponse.StationRevenue> buildRevenueByStation(
            String officeId, long start, long end) {

        Aggregation revAgg = Aggregation.newAggregation(
                Aggregation.match(parcelCriteria(officeId, start, end)),
                Aggregation.group("officeId")
                        .count().as("parcels")
                        .sum("deliveryCost").as("revenue"));
        Map<String, double[]> byStation = new LinkedHashMap<>();
        for (Document doc : mongoTemplate.aggregate(revAgg, Parcel.class, Document.class).getMappedResults()) {
            String oid = doc.getString("_id");
            byStation.put(oid, new double[]{toD(doc.get("revenue")), toD(doc.get("parcels"))});
        }

        Aggregation collAgg = Aggregation.newAggregation(
                Aggregation.match(reconcilCriteria(officeId, start, end, true)),
                Aggregation.group("officeId").sum("payedAmount").as("collected"));
        Map<String, Double> collectedByStation = new HashMap<>();
        for (Document doc : mongoTemplate.aggregate(collAgg, Reconcilations.class, Document.class).getMappedResults()) {
            collectedByStation.put(doc.getString("_id"), toD(doc.get("collected")));
        }

        Map<String, String> officeNames = officeRepository.findAll().stream()
                .collect(Collectors.toMap(o -> o.getId(), o -> o.getName()));

        return byStation.entrySet().stream()
                .map(e -> {
                    String oid = e.getKey();
                    double revenue = e.getValue()[0];
                    long parcels = (long) e.getValue()[1];
                    double coll = collectedByStation.getOrDefault(oid, 0.0);
                    double outs = Math.max(revenue - coll, 0.0);
                    double rate = revenue > 0 ? Math.round(coll / revenue * 10000.0) / 100.0 : 0.0;
                    return RevenueDashboardResponse.StationRevenue.builder()
                            .officeId(oid)
                            .stationName(officeNames.getOrDefault(oid, oid))
                            .parcels(parcels)
                            .revenue(revenue)
                            .collected(coll)
                            .outstanding(outs)
                            .collectionRate(rate)
                            .build();
                })
                .sorted(Comparator.comparingDouble(RevenueDashboardResponse.StationRevenue::getRevenue).reversed())
                .collect(Collectors.toList());
    }

    // ---- Daily payment method trend (Cash / MoMo / Other per day) ----

    private List<RevenueDashboardResponse.DailyPaymentMethodTrend> buildPaymentMethodTrend(
            String officeId, long start, long end) {

        AggregationExpression dayExpr = dayFromEpochMs("createdAt");
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(parcelCriteria(officeId, start, end)),
                Aggregation.project()
                        .and(dayExpr).as("day")
                        .and("paymentMethod").as("paymentMethod")
                        .and("deliveryCost").as("deliveryCost"),
                Aggregation.group("day", "paymentMethod").sum("deliveryCost").as("amount"),
                Aggregation.sort(Sort.Direction.ASC, "_id"));
        List<Document> docs = mongoTemplate.aggregate(agg, Parcel.class, Document.class).getMappedResults();

        Map<String, double[]> byDay = new LinkedHashMap<>();
        for (Document doc : docs) {
            Document id = doc.get("_id", Document.class);
            String day = id.getString("day");
            String method = id.getString("paymentMethod");
            double amount = toD(doc.get("amount"));
            double[] totals = byDay.computeIfAbsent(day, k -> new double[3]);
            String norm = normalizePaymentMethod(method);
            if ("cash".equals(norm)) totals[0] += amount;
            else if ("momo".equals(norm)) totals[1] += amount;
            else totals[2] += amount;
        }

        return generateDates(start, end).stream()
                .map(date -> {
                    double[] t = byDay.getOrDefault(date, new double[3]);
                    return RevenueDashboardResponse.DailyPaymentMethodTrend.builder()
                            .date(date).cash(t[0]).momo(t[1]).other(t[2])
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ---- Revenue by type (POD vs Non-POD) ----

    private RevenueDashboardResponse.RevenueByType buildRevenueByType(String officeId, long start, long end) {
        double pod = sumField("deliveryCost",
                parcelCriteria(officeId, start, end, Criteria.where("isPOD").is(true)), Parcel.class);
        double nonPod = sumField("deliveryCost",
                parcelCriteria(officeId, start, end, Criteria.where("isPOD").is(false)), Parcel.class);
        return RevenueDashboardResponse.RevenueByType.builder().pod(pod).nonPod(nonPod).build();
    }

    // ---- Payment method totals (donut chart) ----

    private List<RevenueDashboardResponse.PaymentMethodTotal> buildPaymentMethodTotals(
            String officeId, long start, long end) {

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(parcelCriteria(officeId, start, end)),
                Aggregation.group("paymentMethod")
                        .sum("deliveryCost").as("amount")
                        .count().as("count"));
        List<Document> docs = mongoTemplate.aggregate(agg, Parcel.class, Document.class).getMappedResults();

        Map<String, double[]> normalized = new LinkedHashMap<>();
        for (Document doc : docs) {
            String method = doc.getString("_id");
            String label = methodLabel(normalizePaymentMethod(method));
            double[] totals = normalized.computeIfAbsent(label, k -> new double[2]);
            totals[0] += toD(doc.get("amount"));
            totals[1] += toL(doc.get("count"));
        }

        return normalized.entrySet().stream()
                .map(e -> RevenueDashboardResponse.PaymentMethodTotal.builder()
                        .method(e.getKey())
                        .amount(e.getValue()[0])
                        .count((long) e.getValue()[1])
                        .build())
                .sorted(Comparator.comparingDouble(RevenueDashboardResponse.PaymentMethodTotal::getAmount).reversed())
                .collect(Collectors.toList());
    }

    // ---- Revenue by day of week ----

    private List<RevenueDashboardResponse.DayOfWeekRevenue> buildRevenueByDayOfWeek(
            String officeId, long start, long end) {

        AggregationExpression dowExpr = ctx -> new Document("$dayOfWeek",
                new Document("$toDate", "$createdAt"));
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(parcelCriteria(officeId, start, end)),
                Aggregation.project().and(dowExpr).as("dow").and("deliveryCost").as("val"),
                Aggregation.group("dow").sum("val").as("revenue"),
                Aggregation.sort(Sort.Direction.ASC, "_id"));
        List<Document> docs = mongoTemplate.aggregate(agg, Parcel.class, Document.class).getMappedResults();

        // MongoDB $dayOfWeek: 1=Sun, 2=Mon, ..., 7=Sat
        String[] dayLabels = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        Map<Integer, Double> dowMap = new HashMap<>();
        for (Document doc : docs) {
            Integer dow = doc.getInteger("_id");
            if (dow != null) dowMap.put(dow, toD(doc.get("revenue")));
        }

        List<RevenueDashboardResponse.DayOfWeekRevenue> result = new ArrayList<>();
        for (int dow = 1; dow <= 7; dow++) {
            result.add(RevenueDashboardResponse.DayOfWeekRevenue.builder()
                    .day(dayLabels[dow - 1])
                    .revenue(dowMap.getOrDefault(dow, 0.0))
                    .build());
        }
        return result;
    }

    // ---- Payment method normalization ----

    private String normalizePaymentMethod(String method) {
        if (method == null || method.isBlank()) return "other";
        String lower = method.toLowerCase();
        if (lower.contains("cash")) return "cash";
        if (lower.contains("mobile") || lower.contains("momo")) return "momo";
        return "other";
    }

    private String methodLabel(String norm) {
        return switch (norm) {
            case "cash" -> "Cash";
            case "momo" -> "Mobile Money";
            default -> "Other";
        };
    }

    // ---- BSON helpers ----

    private static double toD(Object val) {
        return val instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static long toL(Object val) {
        return val instanceof Number n ? n.longValue() : 0L;
    }
}
