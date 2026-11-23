package shortly.mandmcorp.dev.shortly.utils;

import java.util.ArrayList;
import java.util.List;

/** Small geometry helpers for city-scale GPS checks. */
public final class GeoUtils {

    private static final double EARTH_RADIUS_M = 6_371_000d;

    private GeoUtils() {}

    /** Great-circle distance in metres. */
    public static double haversineM(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Distance in metres from point P to segment A–B, using an equirectangular projection
     * centred on P (fine at city scale).
     */
    public static double pointToSegmentM(double pLat, double pLng,
                                         double aLat, double aLng,
                                         double bLat, double bLng) {
        double cos = Math.cos(Math.toRadians(pLat));
        double mPerDegLat = Math.toRadians(1) * EARTH_RADIUS_M;
        double ax = (aLng - pLng) * cos * mPerDegLat, ay = (aLat - pLat) * mPerDegLat;
        double bx = (bLng - pLng) * cos * mPerDegLat, by = (bLat - pLat) * mPerDegLat;
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
        double cx = ax + t * dx, cy = ay + t * dy;
        return Math.sqrt(cx * cx + cy * cy);
    }

    /** Distance in metres from P to the nearest segment of a polyline ([lat,lng] pairs). */
    public static double pointToPolylineM(double pLat, double pLng, List<double[]> line) {
        if (line == null || line.isEmpty()) return Double.POSITIVE_INFINITY;
        if (line.size() == 1) return haversineM(pLat, pLng, line.get(0)[0], line.get(0)[1]);
        double best = Double.POSITIVE_INFINITY;
        for (int i = 1; i < line.size(); i++) {
            double[] a = line.get(i - 1), b = line.get(i);
            best = Math.min(best, pointToSegmentM(pLat, pLng, a[0], a[1], b[0], b[1]));
        }
        return best;
    }

    /** Decodes a Google encoded polyline (precision 5) into [lat,lng] pairs. */
    public static List<double[]> decodePolyline(String encoded) {
        List<double[]> points = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return points;
        int index = 0, lat = 0, lng = 0, len = encoded.length();
        try {
            while (index < len) {
                int[] r = decodeValue(encoded, index);
                lat += r[0];
                index = r[1];
                r = decodeValue(encoded, index);
                lng += r[0];
                index = r[1];
                points.add(new double[] { lat / 1e5, lng / 1e5 });
            }
        } catch (StringIndexOutOfBoundsException e) {
            // Truncated polyline: keep what decoded cleanly.
        }
        return points;
    }

    private static int[] decodeValue(String s, int index) {
        int result = 0, shift = 0, b;
        do {
            b = s.charAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        int value = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new int[] { value, index };
    }
}
