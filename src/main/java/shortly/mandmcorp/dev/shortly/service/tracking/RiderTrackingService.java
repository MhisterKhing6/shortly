package shortly.mandmcorp.dev.shortly.service.tracking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

import shortly.mandmcorp.dev.shortly.dto.response.RiderLocationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.RiderTrackResponse;
import shortly.mandmcorp.dev.shortly.enums.UserRole;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.UserRepository;
import shortly.mandmcorp.dev.shortly.utils.CurrentUser;

@Service
@lombok.extern.slf4j.Slf4j
public class RiderTrackingService {

    /** Jimi accepts up to 100 IMEIs per location call. */
    private static final int LOCATION_BATCH_SIZE = 100;

    private final UserRepository userRepository;
    private final JimiApiService jimiApiService;

    public RiderTrackingService(UserRepository userRepository, JimiApiService jimiApiService) {
        this.userRepository = userRepository;
        this.jimiApiService = jimiApiService;
    }

    // A leading '+' in a query param is decoded as ' ' by URL parsers; restore it.
    private String normalizePhone(String phone) {
        if (phone == null) return null;
        return phone.startsWith(" ") ? "+" + phone.trim() : phone.trim();
    }

    /** Rejects (as not-found) if the rider isn't in the logged-in caller's company. */
    private void assertSameCompany(User rider) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String companyId = (auth != null && auth.getPrincipal() instanceof User caller) ? caller.getCompanyId() : null;
        if (!java.util.Objects.equals(rider.getCompanyId(), companyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rider not found");
        }
    }

    /** Looks up a rider by phone, checking role and (for managers) office scope. */
    public User requireRider(String phoneNumber) {
        User rider = userRepository.findByPhoneNumber(normalizePhone(phoneNumber));
        if (rider == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rider not found");
        }
        assertSameCompany(rider);
        if (rider.getRole() != UserRole.RIDER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a rider");
        }
        CurrentUser.requireRiderInScope(rider);
        return rider;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public RiderLocationResponse getRiderLocationByPhone(String phoneNumber) {
        User rider = requireRider(phoneNumber);

        if (rider.getDeviceImei() == null || rider.getDeviceImei().isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422), "Rider has no GPS device assigned");
        }

        JsonNode result = jimiApiService.getDeviceLocation(rider.getDeviceImei());
        JsonNode locationNode = extractFirstLocation(result, rider.getDeviceImei());

        return JimiApiService.parseLocationNode(locationNode)
                .riderId(rider.getUserId())
                .riderName(rider.getName())
                .riderPhoneNumber(rider.getPhoneNumber())
                .deviceImei(rider.getDeviceImei())
                .build();
    }

    private JsonNode extractFirstLocation(JsonNode result, String imei) {
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from tracking API");
        }

        // JIMI returns an array of device location objects
        if (result.isArray() && result.size() > 0) {
            return result.get(0);
        }

        // Some JIMI responses wrap results under the IMEI key directly
        if (result.has(imei)) {
            return result.get(imei);
        }

        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unexpected tracking API response format");
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public RiderTrackResponse getRiderTrack(String phoneNumber, String beginTime, String endTime) {
        return fetchTrack(requireRider(phoneNumber), beginTime, endTime);
    }

    /** Track lookup without the access check; callers must have checked scope already. */
    public RiderTrackResponse fetchTrack(User rider, String beginTime, String endTime) {
        if (rider.getDeviceImei() == null || rider.getDeviceImei().isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422), "Rider has no GPS device assigned");
        }

        JsonNode result = jimiApiService.getDeviceTrack(rider.getDeviceImei(), beginTime, endTime);

        List<RiderTrackResponse.TrackPoint> points = new ArrayList<>();
        if (result != null && result.isArray()) {
            result.forEach(node -> points.add(JimiApiService.parseTrackPoint(node)));
        }

        return RiderTrackResponse.builder()
                .riderId(rider.getUserId())
                .riderName(rider.getName())
                .deviceImei(rider.getDeviceImei())
                .trackPoints(points)
                .build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public Map<String, String> assignDeviceImei(String phoneNumber, String imei) {
        User rider = requireRider(phoneNumber);
        rider.setDeviceImei(imei.trim());
        userRepository.save(rider);
        return Map.of("message", "Device assigned", "deviceImei", rider.getDeviceImei());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public Map<String, String> removeDevice(String phoneNumber) {
        User rider = requireRider(phoneNumber);
        rider.setDeviceImei(null);
        userRepository.save(rider);
        return Map.of("message", "Device removed");
    }

    /**
     * Live locations for every rider in scope (managers: their office; admins: officeId or all).
     * Riders without a device are included with located=false.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public List<RiderLocationResponse> getRiderLocations(String officeId) {
        String scopedOffice = CurrentUser.scopedOfficeId(officeId);
        List<User> riders = CurrentUser.ridersInScope(userRepository, scopedOffice);

        Map<String, JsonNode> byImei = new HashMap<>();
        List<String> imeis = riders.stream()
                .map(User::getDeviceImei)
                .filter(imei -> imei != null && !imei.isBlank())
                .distinct()
                .toList();
        for (int i = 0; i < imeis.size(); i += LOCATION_BATCH_SIZE) {
            List<String> batch = imeis.subList(i, Math.min(i + LOCATION_BATCH_SIZE, imeis.size()));
            JsonNode result = jimiApiService.getDevicesLocation(batch);
            if (result != null && result.isArray()) {
                result.forEach(node -> {
                    if (node.has("imei")) byImei.put(node.get("imei").asText(), node);
                });
            } else if (result != null && result.isObject()) {
                batch.forEach(imei -> { if (result.has(imei)) byImei.put(imei, result.get(imei)); });
            }
        }

        List<RiderLocationResponse> out = new ArrayList<>();
        for (User rider : riders) {
            JsonNode node = rider.getDeviceImei() == null ? null : byImei.get(rider.getDeviceImei());
            RiderLocationResponse.RiderLocationResponseBuilder b = node != null
                    ? JimiApiService.parseLocationNode(node)
                    : RiderLocationResponse.builder().located(false);
            out.add(b.riderId(rider.getUserId())
                    .riderName(rider.getName())
                    .riderPhoneNumber(rider.getPhoneNumber())
                    .deviceImei(rider.getDeviceImei())
                    .build());
        }
        return out;
    }

    /** Current position of a rider, or empty if they have no device or the tracker can't be reached. Never throws. */
    public Optional<RiderLocationResponse> tryGetLocation(User rider) {
        if (rider == null || rider.getDeviceImei() == null || rider.getDeviceImei().isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode result = jimiApiService.getDeviceLocation(rider.getDeviceImei());
            RiderLocationResponse location = JimiApiService.parseLocationNode(extractFirstLocation(result, rider.getDeviceImei())).build();
            if (location.getLat() == null || location.getLng() == null) return Optional.empty();
            return Optional.of(location);
        } catch (Exception e) {
            log.warn("Could not read GPS position for rider {}: {}", rider.getUserId(), e.getMessage());
            return Optional.empty();
        }
    }
}
