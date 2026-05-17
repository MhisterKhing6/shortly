package shortly.mandmcorp.dev.shortly.service.tracking;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

import shortly.mandmcorp.dev.shortly.dto.response.RiderLocationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.RiderTrackResponse;
import shortly.mandmcorp.dev.shortly.enums.UserRole;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.UserRepository;

@Service
public class RiderTrackingService {

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

    public RiderLocationResponse getRiderLocationByPhone(String phoneNumber) {
        User rider = userRepository.findByPhoneNumber(normalizePhone(phoneNumber));

        if (rider == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rider not found with phone number: " + phoneNumber);
        }

        if (rider.getRole() != UserRole.RIDER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a rider");
        }

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

    public RiderTrackResponse getRiderTrack(String phoneNumber, String beginTime, String endTime) {
        String riderPhoneNumber = normalizePhone(phoneNumber);
        User rider = userRepository.findByPhoneNumber(riderPhoneNumber);

        if (rider == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rider not found with phone number: " + riderPhoneNumber);
        }

        if (rider.getRole() != UserRole.RIDER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a rider");
        }

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

    public String assignDeviceImei(String phoneNumber, String imei) {
        User rider = userRepository.findByPhoneNumber(phoneNumber);

        if (rider == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rider not found with phone number: " + phoneNumber);
        }

        if (rider.getRole() != UserRole.RIDER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a rider");
        }

        rider.setDeviceImei(imei);
        userRepository.save(rider);
        return imei;
    }
}
