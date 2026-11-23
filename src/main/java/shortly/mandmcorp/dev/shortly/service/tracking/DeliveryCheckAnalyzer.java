package shortly.mandmcorp.dev.shortly.service.tracking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryCheckResponse;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryCheckResponse.Delivery;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryCheckResponse.Detour;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryCheckResponse.OffTask;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryCheckResponse.OffTaskStop;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryCheckResponse.Result;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryCheckResponse.Summary;
import shortly.mandmcorp.dev.shortly.utils.GeoUtils;

/**
 * Pure implementation of the delivery-check (Part D) and off-task (Part E, with the Part F5 plan
 * corridor) rules. No I/O: callers pass GPS points, deliveries, the station and any route plans.
 */
public class DeliveryCheckAnalyzer {

    private static final long MINUTE = 60_000L;

    /** One GPS fix. */
    @Data
    @AllArgsConstructor
    public static class Point {
        private double lat;
        private double lng;
        private long time;
    }

    /** One parcel the rider was given, as seen by the check. */
    @Data
    @Builder
    public static class DeliveryInput {
        private String assignmentId;
        private String parcelId;
        private String receiverName;
        private String receiverLocationName;
        private Double lat;
        private Double lng;
        /** DELIVERED, RETURNED, or the pending assignment status. */
        private String status;
        private long assignedAt;
        /** When marked DELIVERED/RETURNED; null if not finished. */
        private Long completedAt;
        /** From D1, when captured at marking time. */
        private Double markedDistanceFromDropoffM;

        boolean finished() {
            return completedAt != null;
        }

        boolean hasPin() {
            return lat != null && lng != null;
        }
    }

    /** A saved route plan's line and the corridor that applies to it. */
    @Data
    @AllArgsConstructor
    public static class PlanLine {
        private List<double[]> path;
        private double corridorM;
    }

    private final TrackingRules rules;

    public DeliveryCheckAnalyzer(TrackingRules rules) {
        this.rules = rules;
    }

    public DeliveryCheckResponse analyze(List<Point> rawPoints, List<DeliveryInput> inputs,
                                         Double stationLat, Double stationLng, List<PlanLine> plans) {
        List<Point> points = new ArrayList<>(rawPoints);
        points.sort(Comparator.comparingLong(Point::getTime));

        List<Delivery> deliveries = new ArrayList<>();
        List<Reached> reachedList = new ArrayList<>();
        Summary summary = new Summary();
        for (DeliveryInput in : inputs) {
            Delivery d = checkDelivery(in, points);
            deliveries.add(d);
            summary.setDeliveries(summary.getDeliveries() + 1);
            switch (d.getResult()) {
                case REACHED -> {
                    summary.setReached(summary.getReached() + 1);
                    reachedList.add(new Reached(in, d.getArrivedAt(), d.getLeftAt()));
                }
                case NOT_REACHED -> summary.setNotReached(summary.getNotReached() + 1);
                case NOT_FINISHED -> summary.setNotFinished(summary.getNotFinished() + 1);
                case NO_PIN -> summary.setNoPin(summary.getNoPin() + 1);
                case NO_GPS -> summary.setNoGps(summary.getNoGps() + 1);
            }
            if (Boolean.TRUE.equals(d.getMarkedAway())) summary.setMarkedAway(summary.getMarkedAway() + 1);
        }

        double distanceKm = round1(pathM(points, 0, points.size() - 1) / 1000);
        List<Stop> stops = findStops(points);
        long moving = movingMs(points);
        long stopped = stops.stream().mapToLong(s -> s.end - s.start).sum();
        summary.setDistanceKm(distanceKm);
        summary.setMovingMinutes(moving / MINUTE);
        summary.setStoppedMinutes(stopped / MINUTE);

        OffTask offTask = analyzeOffTask(points, inputs, reachedList, stops, stationLat, stationLng,
                plans == null ? List.of() : plans, distanceKm);

        return DeliveryCheckResponse.builder()
                .summary(summary)
                .deliveries(deliveries)
                .offTask(offTask)
                .build();
    }

    // ------------------------------------------------------------------ Part D

    Delivery checkDelivery(DeliveryInput in, List<Point> points) {
        Delivery.DeliveryBuilder d = Delivery.builder()
                .assignmentId(in.getAssignmentId())
                .parcelId(in.getParcelId())
                .receiverName(in.getReceiverName())
                .receiverLocationName(in.getReceiverLocationName())
                .receiverLatitude(in.getLat())
                .receiverLongitude(in.getLng())
                .status(in.getStatus())
                .completedAt(in.getCompletedAt());

        if (!in.hasPin()) {
            return d.result(Result.NO_PIN).build();
        }

        long windowStart = in.getAssignedAt();
        long windowEnd = in.finished() ? in.getCompletedAt() + rules.getVisitGraceMin() * MINUTE : Long.MAX_VALUE;

        Long markedDistance = in.getMarkedDistanceFromDropoffM() != null
                ? Long.valueOf(Math.round(in.getMarkedDistanceFromDropoffM()))
                : markedDistanceFromTrack(in, points);
        if (markedDistance != null) {
            d.markedDistanceFromDropoffM(markedDistance);
            d.markedAway(markedDistance > rules.getMarkedAwayM());
        }

        List<Point> window = new ArrayList<>();
        for (Point p : points) {
            if (p.getTime() >= windowStart && p.getTime() <= windowEnd) window.add(p);
        }
        if (window.isEmpty()) {
            return d.result(Result.NO_GPS).build();
        }

        double closest = Double.POSITIVE_INFINITY;
        long closestAt = 0;
        int firstInside = -1;
        for (int i = 0; i < window.size(); i++) {
            Point p = window.get(i);
            double dist = GeoUtils.haversineM(p.getLat(), p.getLng(), in.getLat(), in.getLng());
            if (dist < closest) {
                closest = dist;
                closestAt = p.getTime();
            }
            if (firstInside < 0 && dist <= rules.getVisitRadiusM()) firstInside = i;
        }

        Long arrivedAt = null, leftAt = null;
        if (firstInside >= 0) {
            // The first visit lasts while consecutive fixes stay inside the radius with no long gap.
            int lastInside = firstInside;
            for (int i = firstInside + 1; i < window.size(); i++) {
                Point p = window.get(i);
                if (isGap(window.get(i - 1), p)) break;
                if (GeoUtils.haversineM(p.getLat(), p.getLng(), in.getLat(), in.getLng()) > rules.getVisitRadiusM()) break;
                lastInside = i;
            }
            arrivedAt = window.get(firstInside).getTime();
            leftAt = window.get(lastInside).getTime();
        }

        d.closestDistanceM(Math.round(closest));
        if (arrivedAt != null) {
            return d.result(Result.REACHED)
                    .arrivedAt(arrivedAt)
                    .leftAt(leftAt)
                    .minutesThere((leftAt - arrivedAt) / MINUTE)
                    .build();
        }
        d.closestAt(closestAt);
        return d.result(in.finished() ? Result.NOT_REACHED : Result.NOT_FINISHED).build();
    }

    /** Distance from the drop-off of the GPS fix nearest in time to completedAt (within the max gap). */
    private Long markedDistanceFromTrack(DeliveryInput in, List<Point> points) {
        if (!in.finished() || points.isEmpty()) return null;
        Point best = null;
        long bestDt = Long.MAX_VALUE;
        for (Point p : points) {
            long dt = Math.abs(p.getTime() - in.getCompletedAt());
            if (dt < bestDt) {
                bestDt = dt;
                best = p;
            }
        }
        if (best == null || bestDt > rules.getMaxGapMin() * MINUTE) return null;
        return Math.round(GeoUtils.haversineM(best.getLat(), best.getLng(), in.getLat(), in.getLng()));
    }

    // ------------------------------------------------------------------ movement and stops

    private record Stop(long start, long end, double lat, double lng) {}

    private record Reached(DeliveryInput input, long arrivedAt, long leftAt) {}

    private double speedKmh(Point a, Point b) {
        long dt = b.getTime() - a.getTime();
        if (dt <= 0) return 0;
        return GeoUtils.haversineM(a.getLat(), a.getLng(), b.getLat(), b.getLng()) / 1000 / (dt / 3_600_000d);
    }

    private boolean isGap(Point a, Point b) {
        return b.getTime() - a.getTime() > rules.getMaxGapMin() * MINUTE;
    }

    private long movingMs(List<Point> pts) {
        long total = 0;
        for (int i = 1; i < pts.size(); i++) {
            Point a = pts.get(i - 1), b = pts.get(i);
            if (isGap(a, b)) continue;
            if (speedKmh(a, b) >= rules.getStopSpeedKmh()) total += b.getTime() - a.getTime();
        }
        return total;
    }

    /** Stretches below the stop speed lasting at least the minimum, broken at long gaps. */
    private List<Stop> findStops(List<Point> pts) {
        List<Stop> stops = new ArrayList<>();
        int runStart = -1;
        for (int i = 1; i <= pts.size(); i++) {
            boolean slow = i < pts.size()
                    && !isGap(pts.get(i - 1), pts.get(i))
                    && speedKmh(pts.get(i - 1), pts.get(i)) < rules.getStopSpeedKmh();
            if (slow) {
                if (runStart < 0) runStart = i - 1;
            } else if (runStart >= 0) {
                int runEnd = i - 1;
                long start = pts.get(runStart).getTime(), end = pts.get(runEnd).getTime();
                if (end - start >= rules.getStopMinMinutes() * MINUTE) {
                    double lat = 0, lng = 0;
                    for (int k = runStart; k <= runEnd; k++) {
                        lat += pts.get(k).getLat();
                        lng += pts.get(k).getLng();
                    }
                    int n = runEnd - runStart + 1;
                    stops.add(new Stop(start, end, lat / n, lng / n));
                }
                runStart = -1;
            }
        }
        return stops;
    }

    private static double pathM(List<Point> pts, int from, int to) {
        double m = 0;
        for (int i = Math.max(from, 0) + 1; i <= to && i < pts.size(); i++) {
            Point a = pts.get(i - 1), b = pts.get(i);
            m += GeoUtils.haversineM(a.getLat(), a.getLng(), b.getLat(), b.getLng());
        }
        return m;
    }

    // ------------------------------------------------------------------ Part E

    private record Anchor(String label, double lat, double lng) {}

    private OffTask analyzeOffTask(List<Point> points, List<DeliveryInput> inputs, List<Reached> reached,
                                   List<Stop> stops, Double stationLat, Double stationLng,
                                   List<PlanLine> plans, double distanceKm) {
        OffTask.OffTaskBuilder out = OffTask.builder().distanceKm(distanceKm);
        boolean hasStation = stationLat != null && stationLng != null;

        // Off-task stops: not near any pin, the station, or the first/last GPS point.
        List<double[]> allowed = new ArrayList<>();
        for (DeliveryInput in : inputs) if (in.hasPin()) allowed.add(new double[] { in.getLat(), in.getLng() });
        if (hasStation) allowed.add(new double[] { stationLat, stationLng });
        if (!points.isEmpty()) {
            allowed.add(new double[] { points.get(0).getLat(), points.get(0).getLng() });
            Point last = points.get(points.size() - 1);
            allowed.add(new double[] { last.getLat(), last.getLng() });
        }
        List<OffTaskStop> offStops = new ArrayList<>();
        long offStopMs = 0;
        for (Stop s : stops) {
            boolean nearAllowed = allowed.stream().anyMatch(a ->
                    GeoUtils.haversineM(s.lat(), s.lng(), a[0], a[1]) <= rules.getOffTaskStopRadiusM());
            if (!nearAllowed) {
                offStops.add(OffTaskStop.builder().start(s.start()).end(s.end())
                        .lat(round5(s.lat())).lng(round5(s.lng()))
                        .minutes((s.end() - s.start()) / MINUTE).build());
                offStopMs += s.end() - s.start();
            }
        }
        out.offTaskStops(offStops).offTaskStopMinutes(offStopMs / MINUTE);

        if (reached.isEmpty() || points.isEmpty()) {
            return out.judged(false)
                    .reason(points.isEmpty() ? "No GPS points in this period"
                            : "No pinned drop-offs were reached in this period")
                    .build();
        }

        List<Reached> ordered = new ArrayList<>(reached);
        ordered.sort(Comparator.comparingLong(Reached::arrivedAt));

        Point first = points.get(0), last = points.get(points.size() - 1);
        Anchor startAnchor = hasStation ? new Anchor("Station", stationLat, stationLng)
                : new Anchor("Start", first.getLat(), first.getLng());
        Anchor endAnchor = hasStation ? new Anchor("Station", stationLat, stationLng)
                : new Anchor("End", last.getLat(), last.getLng());

        // Leg windows: [leave anchor A, arrive anchor B]
        List<Anchor> anchors = new ArrayList<>();
        List<long[]> windows = new ArrayList<>();
        anchors.add(startAnchor);
        long firstArrival = ordered.get(0).arrivedAt();
        long legStart = hasStation ? lastNearBefore(points, startAnchor, firstArrival, first.getTime()) : first.getTime();
        for (Reached r : ordered) {
            anchors.add(new Anchor(label(r.input()), r.input().getLat(), r.input().getLng()));
            windows.add(new long[] { legStart, r.arrivedAt() });
            legStart = r.leftAt();
        }
        anchors.add(endAnchor);
        long lastLeft = ordered.get(ordered.size() - 1).leftAt();
        long legEnd = hasStation ? firstNearAfter(points, endAnchor, lastLeft, last.getTime()) : last.getTime();
        windows.add(new long[] { legStart, legEnd });

        double directM = 0;
        for (int i = 1; i < anchors.size(); i++) {
            Anchor a = anchors.get(i - 1), b = anchors.get(i);
            directM += GeoUtils.haversineM(a.lat(), a.lng(), b.lat(), b.lng());
        }

        List<Detour> detours = new ArrayList<>();
        for (int leg = 0; leg < windows.size(); leg++) {
            detours.addAll(findDetours(points, anchors.get(leg), anchors.get(leg + 1), windows.get(leg), plans));
        }
        long offRouteMs = detours.stream().mapToLong(dt -> dt.getEnd() - dt.getStart()).sum();
        double offRouteKm = detours.stream().mapToDouble(Detour::getKmRidden).sum();

        return out.judged(true)
                .directKm(round1(directM / 1000))
                .offRouteMinutes(offRouteMs / MINUTE)
                .offRouteKm(round1(offRouteKm))
                .detours(detours)
                .build();
    }

    private static String label(DeliveryInput in) {
        if (in.getReceiverName() != null && !in.getReceiverName().isBlank()) return in.getReceiverName();
        if (in.getReceiverLocationName() != null && !in.getReceiverLocationName().isBlank()) return in.getReceiverLocationName();
        return in.getParcelId();
    }

    private long lastNearBefore(List<Point> pts, Anchor a, long before, long fallback) {
        long found = -1;
        for (Point p : pts) {
            if (p.getTime() >= before) break;
            if (GeoUtils.haversineM(p.getLat(), p.getLng(), a.lat(), a.lng()) <= rules.getVisitRadiusM()) found = p.getTime();
        }
        return found >= 0 ? found : fallback;
    }

    private long firstNearAfter(List<Point> pts, Anchor a, long after, long fallback) {
        for (Point p : pts) {
            if (p.getTime() <= after) continue;
            if (GeoUtils.haversineM(p.getLat(), p.getLng(), a.lat(), a.lng()) <= rules.getVisitRadiusM()) return p.getTime();
        }
        return fallback;
    }

    private List<Detour> findDetours(List<Point> pts, Anchor from, Anchor to, long[] window, List<PlanLine> plans) {
        List<Detour> out = new ArrayList<>();
        // indices of points inside the leg window
        int lo = -1, hi = -1;
        for (int i = 0; i < pts.size(); i++) {
            long t = pts.get(i).getTime();
            if (t >= window[0] && t <= window[1]) {
                if (lo < 0) lo = i;
                hi = i;
            }
        }
        if (lo < 0) return out;

        int runStart = -1;
        double runMax = 0;
        for (int i = lo; i <= hi + 1; i++) {
            boolean off = false;
            double dist = 0;
            if (i <= hi) {
                Point p = pts.get(i);
                dist = GeoUtils.pointToSegmentM(p.getLat(), p.getLng(), from.lat(), from.lng(), to.lat(), to.lng());
                off = dist > rules.getDetourCorridorM() && !nearPlan(p, plans);
            }
            boolean gapBreak = runStart >= 0 && i <= hi && isGap(pts.get(i - 1), pts.get(i));
            if (off && !gapBreak) {
                if (runStart < 0) {
                    runStart = i;
                    runMax = 0;
                }
                runMax = Math.max(runMax, dist);
                continue;
            }
            if (runStart >= 0) {
                int runEnd = i - 1;
                long start = pts.get(runStart).getTime(), end = pts.get(runEnd).getTime();
                if (end - start >= rules.getDetourMinMinutes() * MINUTE) {
                    int before = Math.max(runStart - 1, lo);
                    int after = Math.min(runEnd + 1, hi);
                    out.add(Detour.builder()
                            .start(start).end(end)
                            .fromLabel(from.label()).toLabel(to.label())
                            .maxOffRouteM(Math.round(runMax))
                            .kmRidden(round1(pathM(pts, before, after) / 1000))
                            .build());
                }
                runStart = -1;
            }
            if (off && gapBreak) { // a gap ends one detour; this point may start the next
                runStart = i;
                runMax = dist;
            }
        }
        return out;
    }

    private static boolean nearPlan(Point p, List<PlanLine> plans) {
        for (PlanLine plan : plans) {
            if (plan.getPath() == null || plan.getPath().isEmpty()) continue;
            if (GeoUtils.pointToPolylineM(p.getLat(), p.getLng(), plan.getPath()) <= plan.getCorridorM()) return true;
        }
        return false;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round5(double v) {
        return Math.round(v * 100_000.0) / 100_000.0;
    }
}
