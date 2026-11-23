package shortly.mandmcorp.dev.shortly.service.tracking;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import shortly.mandmcorp.dev.shortly.dto.response.DeliveryCheckResponse;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryCheckResponse.Delivery;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryCheckResponse.Result;
import shortly.mandmcorp.dev.shortly.service.tracking.DeliveryCheckAnalyzer.DeliveryInput;
import shortly.mandmcorp.dev.shortly.service.tracking.DeliveryCheckAnalyzer.PlanLine;
import shortly.mandmcorp.dev.shortly.service.tracking.DeliveryCheckAnalyzer.Point;

class DeliveryCheckAnalyzerTest {

    private static final long MIN = 60_000L;
    private static final long T0 = 1_763_712_000_000L; // arbitrary day start
    // ~111 m per 0.001 deg latitude
    private static final double STATION_LAT = 5.5600, STATION_LNG = -0.2000;
    private static final double A_LAT = 5.5800, A_LNG = -0.2000;   // ~2.2 km north
    private static final double B_LAT = 5.6000, B_LNG = -0.2000;   // ~4.4 km north

    private final DeliveryCheckAnalyzer analyzer = new DeliveryCheckAnalyzer(TrackingRules.defaults());

    /** Straight ride from (lat1,lng1) to (lat2,lng2), one fix per minute. */
    private static void ride(List<Point> pts, double lat1, double lng1, double lat2, double lng2, long start, int minutes) {
        for (int i = 0; i <= minutes; i++) {
            double f = (double) i / minutes;
            pts.add(new Point(lat1 + (lat2 - lat1) * f, lng1 + (lng2 - lng1) * f, start + i * MIN));
        }
    }

    private static void wait(List<Point> pts, double lat, double lng, long start, int minutes) {
        for (int i = 1; i <= minutes; i++) pts.add(new Point(lat, lng, start + i * MIN));
    }

    private static DeliveryInput delivery(String id, String name, Double lat, Double lng, Long completedAt) {
        return DeliveryInput.builder().assignmentId("a1").parcelId(id).receiverName(name)
                .lat(lat).lng(lng).assignedAt(T0).completedAt(completedAt)
                .status(completedAt != null ? "DELIVERED" : "ASSIGNED").build();
    }

    /** Station → A (wait 6) → B (wait 6) → station. */
    private static List<Point> normalDay() {
        List<Point> pts = new ArrayList<>();
        ride(pts, STATION_LAT, STATION_LNG, A_LAT, A_LNG, T0, 10);
        wait(pts, A_LAT, A_LNG, T0 + 10 * MIN, 6);
        ride(pts, A_LAT, A_LNG, B_LAT, B_LNG, T0 + 17 * MIN, 10);
        wait(pts, B_LAT, B_LNG, T0 + 27 * MIN, 6);
        ride(pts, B_LAT, B_LNG, STATION_LAT, STATION_LNG, T0 + 34 * MIN, 20);
        return pts;
    }

    @Test
    void reachedDropoffsAreReportedWithArrivalAndDeparture() {
        DeliveryCheckResponse r = analyzer.analyze(normalDay(),
                List.of(delivery("P1", "Akosua", A_LAT, A_LNG, T0 + 14 * MIN),
                        delivery("P2", "Efua", B_LAT, B_LNG, T0 + 31 * MIN)),
                STATION_LAT, STATION_LNG, List.of());

        assertEquals(2, r.getSummary().getReached());
        Delivery p1 = r.getDeliveries().get(0);
        assertEquals(Result.REACHED, p1.getResult());
        assertEquals(T0 + 10 * MIN, p1.getArrivedAt());
        // the next ride's first fix (T0+17) is still at A
        assertEquals(T0 + 17 * MIN, p1.getLeftAt());
        assertEquals(7, p1.getMinutesThere());
        assertEquals(0, r.getSummary().getMarkedAway());
        assertTrue(r.getOffTask().isJudged());
        assertTrue(r.getOffTask().getDetours().isEmpty());
        assertTrue(r.getOffTask().getOffTaskStops().isEmpty(), "stops at drop-offs aren't off-task");
        assertEquals(8.9, r.getOffTask().getDirectKm(), 0.2);
        assertEquals(2, r.getSummary().getStoppedMinutes() / 6);
    }

    @Test
    void unreachedPinnedDeliveryReportsClosestApproachAndMarkedAway() {
        double farLat = 5.5800, farLng = -0.1800; // ~2.2 km east of A
        DeliveryCheckResponse r = analyzer.analyze(normalDay(),
                List.of(delivery("P3", "Mawuli", farLat, farLng, T0 + 14 * MIN)),
                STATION_LAT, STATION_LNG, List.of());
        Delivery d = r.getDeliveries().get(0);
        assertEquals(Result.NOT_REACHED, d.getResult());
        assertTrue(d.getClosestDistanceM() > 2000);
        assertNotNull(d.getClosestAt());
        assertTrue(d.getMarkedAway(), "marked delivered ~2 km from the pin");
        assertEquals(1, r.getSummary().getMarkedAway());
        assertFalse(r.getOffTask().isJudged());
        assertNotNull(r.getOffTask().getReason());
    }

    @Test
    void noPinNoGpsAndNotFinished() {
        DeliveryCheckResponse r = analyzer.analyze(normalDay(),
                List.of(delivery("P4", "NoPin", null, null, null),
                        DeliveryInput.builder().parcelId("P5").lat(A_LAT).lng(A_LNG)
                                .assignedAt(T0 + 500 * MIN).build(),
                        delivery("P6", "Pending", 5.7, -0.2, null)),
                STATION_LAT, STATION_LNG, List.of());
        assertEquals(Result.NO_PIN, r.getDeliveries().get(0).getResult());
        assertEquals(Result.NO_GPS, r.getDeliveries().get(1).getResult());
        assertEquals(Result.NOT_FINISHED, r.getDeliveries().get(2).getResult());
    }

    @Test
    void sideTripBetweenDropoffsIsADetourWithAnOffTaskStop() {
        double sideLat = 5.5900, sideLng = -0.1650; // ~3.8 km east of the A→B line
        List<Point> pts = new ArrayList<>();
        ride(pts, STATION_LAT, STATION_LNG, A_LAT, A_LNG, T0, 10);
        wait(pts, A_LAT, A_LNG, T0 + 10 * MIN, 5);
        ride(pts, A_LAT, A_LNG, sideLat, sideLng, T0 + 16 * MIN, 10);
        wait(pts, sideLat, sideLng, T0 + 26 * MIN, 20);
        ride(pts, sideLat, sideLng, B_LAT, B_LNG, T0 + 47 * MIN, 10);
        wait(pts, B_LAT, B_LNG, T0 + 57 * MIN, 5);
        ride(pts, B_LAT, B_LNG, STATION_LAT, STATION_LNG, T0 + 63 * MIN, 20);

        DeliveryCheckResponse r = analyzer.analyze(pts,
                List.of(delivery("P1", "Akosua Ofori", A_LAT, A_LNG, T0 + 12 * MIN),
                        delivery("P2", "Efua Appiah", B_LAT, B_LNG, T0 + 60 * MIN)),
                STATION_LAT, STATION_LNG, List.of());

        assertTrue(r.getOffTask().isJudged());
        assertEquals(1, r.getOffTask().getDetours().size());
        var detour = r.getOffTask().getDetours().get(0);
        assertEquals("Akosua Ofori", detour.getFromLabel());
        assertEquals("Efua Appiah", detour.getToLabel());
        assertTrue(detour.getMaxOffRouteM() > 3000);
        assertTrue(detour.getEnd() - detour.getStart() >= 20 * MIN);
        assertEquals(1, r.getOffTask().getOffTaskStops().size());
        // stationary from arrival (T0+26) to the next ride's first fix (T0+47)
        assertEquals(21, r.getOffTask().getOffTaskStopMinutes());
        assertTrue(r.getOffTask().getDistanceKm() > r.getOffTask().getDirectKm());
    }

    @Test
    void planCorridorSuppressesDetourOnPlannedRoad() {
        double sideLat = 5.5900, sideLng = -0.1650;
        List<Point> pts = new ArrayList<>();
        ride(pts, STATION_LAT, STATION_LNG, A_LAT, A_LNG, T0, 10);
        wait(pts, A_LAT, A_LNG, T0 + 10 * MIN, 5);
        ride(pts, A_LAT, A_LNG, sideLat, sideLng, T0 + 16 * MIN, 10);
        ride(pts, sideLat, sideLng, B_LAT, B_LNG, T0 + 27 * MIN, 10);
        wait(pts, B_LAT, B_LNG, T0 + 37 * MIN, 5);

        // The planned road goes via the "side" point, so riding it is not a detour.
        List<double[]> planPath = List.of(new double[] { A_LAT, A_LNG }, new double[] { sideLat, sideLng },
                new double[] { B_LAT, B_LNG });
        DeliveryCheckResponse r = analyzer.analyze(pts,
                List.of(delivery("P1", "A", A_LAT, A_LNG, T0 + 12 * MIN),
                        delivery("P2", "B", B_LAT, B_LNG, T0 + 40 * MIN)),
                STATION_LAT, STATION_LNG, List.of(new PlanLine(planPath, 500)));
        assertTrue(r.getOffTask().getDetours().isEmpty());
    }

    @Test
    void capturedMarkedDistanceWinsOverTrack() {
        DeliveryInput in = DeliveryInput.builder().parcelId("P1").lat(A_LAT).lng(A_LNG)
                .assignedAt(T0).completedAt(T0 + 14 * MIN).markedDistanceFromDropoffM(450.0).build();
        DeliveryCheckResponse r = analyzer.analyze(normalDay(), List.of(in), STATION_LAT, STATION_LNG, List.of());
        assertEquals(450L, r.getDeliveries().get(0).getMarkedDistanceFromDropoffM());
        assertTrue(r.getDeliveries().get(0).getMarkedAway());
    }
}
