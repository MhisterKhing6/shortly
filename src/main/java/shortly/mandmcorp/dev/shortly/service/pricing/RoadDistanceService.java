package shortly.mandmcorp.dev.shortly.service.pricing;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.model.DistanceCacheEntry;
import shortly.mandmcorp.dev.shortly.utils.GeoUtils;

/**
 * Road distance from a station to a pin, via the Google Routes API (TWO_WHEELER, then DRIVE).
 * Cached by station + pin rounded to ~50 m. Without a key, or if Google fails, uses the straight
 * line × 1.35 and reports ESTIMATE.
 */
@Service
@Slf4j
public class RoadDistanceService {

    static final double ESTIMATE_FACTOR = 1.35;
    /** 0.0005° ≈ 55 m of latitude. */
    private static final double GRID = 0.0005;
    private static final String ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes";

    public record Distance(double km, String source, Double minutes) {}

    private final MongoTemplate mongoTemplate;
    private final WebClient webClient;
    private final String apiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RoadDistanceService(MongoTemplate mongoTemplate, WebClient webClient,
                               @Value("${shortly.google.maps-api-key:}") String apiKey) {
        this.mongoTemplate = mongoTemplate;
        this.webClient = webClient;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public Distance distance(String officeId, double fromLat, double fromLng, double toLat, double toLng) {
        String key = officeId + "|" + grid(toLat) + "|" + grid(toLng);
        DistanceCacheEntry cached = mongoTemplate.findOne(new Query(Criteria.where("key").is(key)), DistanceCacheEntry.class);
        if (cached != null) return new Distance(cached.getKm(), "ROAD", cached.getMinutes());

        if (!apiKey.isEmpty()) {
            for (String mode : List.of("TWO_WHEELER", "DRIVE")) {
                Distance road = google(mode, fromLat, fromLng, toLat, toLng);
                if (road != null) {
                    try {
                        mongoTemplate.insert(DistanceCacheEntry.builder().key(key).km(road.km())
                                .minutes(road.minutes() != null ? road.minutes() : 0).createdAt(System.currentTimeMillis()).build());
                    } catch (DuplicateKeyException ignored) {
                        // another request cached it first
                    }
                    return road;
                }
            }
        }
        double straightKm = GeoUtils.haversineM(fromLat, fromLng, toLat, toLng) / 1000.0;
        return new Distance(straightKm * ESTIMATE_FACTOR, "ESTIMATE", null);
    }

    private Distance google(String mode, double fromLat, double fromLng, double toLat, double toLng) {
        try {
            Map<String, Object> body = Map.of(
                    "origin", Map.of("location", Map.of("latLng", Map.of("latitude", fromLat, "longitude", fromLng))),
                    "destination", Map.of("location", Map.of("latLng", Map.of("latitude", toLat, "longitude", toLng))),
                    "travelMode", mode);
            String raw = webClient.post().uri(ROUTES_URL)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", "routes.distanceMeters,routes.duration")
                    .header("Content-Type", "application/json")
                    .bodyValue(objectMapper.writeValueAsString(body))
                    .retrieve().bodyToMono(String.class)
                    .block(Duration.ofSeconds(6));
            JsonNode routes = raw == null ? null : objectMapper.readTree(raw).path("routes");
            if (routes == null || !routes.isArray() || routes.isEmpty() || !routes.get(0).has("distanceMeters")) return null;
            double km = routes.get(0).get("distanceMeters").asDouble() / 1000.0;
            Double minutes = null;
            String dur = routes.get(0).path("duration").asText("");
            if (dur.endsWith("s")) minutes = Double.parseDouble(dur.substring(0, dur.length() - 1)) / 60.0;
            return new Distance(km, "ROAD", minutes);
        } catch (Exception e) {
            log.warn("Google Routes ({}) failed: {}", mode, e.getMessage());
            return null;
        }
    }

    private static String grid(double v) {
        return String.format(java.util.Locale.ROOT, "%.4f", Math.round(v / GRID) * GRID);
    }
}
