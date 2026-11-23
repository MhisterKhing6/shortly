package shortly.mandmcorp.dev.shortly.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class GeoUtilsTest {

    @Test
    void decodesGooglesReferencePolyline() {
        List<double[]> pts = GeoUtils.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        assertEquals(3, pts.size());
        assertArrayEquals(new double[] { 38.5, -120.2 }, pts.get(0), 1e-9);
        assertArrayEquals(new double[] { 40.7, -120.95 }, pts.get(1), 1e-9);
        assertArrayEquals(new double[] { 43.252, -126.453 }, pts.get(2), 1e-9);
    }

    @Test
    void pointToSegmentIsPerpendicularDistanceAndClampsToEnds() {
        // 0.01 deg of longitude at the equator ≈ 1112 m
        double mid = GeoUtils.pointToSegmentM(0.01, 0.005, 0, 0, 0, 0.01);
        assertEquals(1112, mid, 5);
        double beyond = GeoUtils.pointToSegmentM(0, 0.02, 0, 0, 0, 0.01);
        assertEquals(1112, beyond, 5);
    }

    @Test
    void haversineMatchesKnownDistance() {
        // one degree of latitude ≈ 111.195 km on a 6371 km sphere
        assertEquals(111_195, GeoUtils.haversineM(5.0, -0.2, 6.0, -0.2), 1);
    }
}
