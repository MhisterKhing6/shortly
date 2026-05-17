package shortly.mandmcorp.dev.shortly.service.tracking;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.config.JimiConfig;
import shortly.mandmcorp.dev.shortly.dto.response.RiderLocationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.RiderTrackResponse;

@Service
@Slf4j
public class JimiApiService {

    private final JimiConfig jimiConfig;
    private final WebClient webClient;
    private final JimiTokenService tokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JimiApiService(JimiConfig jimiConfig, WebClient webClient, JimiTokenService tokenService) {
        this.jimiConfig = jimiConfig;
        this.webClient = webClient;
        this.tokenService = tokenService;
    }

    public JsonNode getDeviceLocation(String imei) {
        Map<String, String> params = buildBaseParams("jimi.device.location.get");
        params.put("imeis", imei);

        return callJimi(params);
    }

    public JsonNode getDevicesLocation(List<String> imeis) {
        Map<String, String> params = buildBaseParams("jimi.device.location.get");
        params.put("imeis", String.join(",", imeis));

        return callJimi(params);
    }

    public JsonNode getDeviceTrack(String imei, String beginTime, String endTime) {
        Map<String, String> params = buildBaseParams("jimi.device.track.list");
        params.put("imei", imei);
        params.put("begin_time", beginTime);
        params.put("end_time", endTime);
        params.put("page_index", "1");
        params.put("page_size", "1000");

        return callJimi(params);
    }

    private Map<String, String> buildBaseParams(String method) {
        Map<String, String> params = new TreeMap<>();
        params.put("method", method);
        params.put("timestamp", tokenService.getCurrentTimestamp());
        params.put("app_key", jimiConfig.getAppKey());
        params.put("sign_method", "md5");
        params.put("v", "1.0");
        params.put("format", "json");
        params.put("access_token", tokenService.getAccessToken());
        return params;
    }

    private JsonNode callJimi(Map<String, String> params) {
        String sign = tokenService.computeSign(params);
        params.put("sign", sign);

        String body = buildFormBody(params);

        try {
            byte[] responseBytes = webClient.post()
                    .uri(jimiConfig.getBaseUrl())
                    .bodyValue(body)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .exchangeToMono(response -> response.bodyToMono(byte[].class))
                    .block();

            if (responseBytes == null || responseBytes.length == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No response from JIMI API");
            }

            String rawResponse = new String(responseBytes, StandardCharsets.UTF_8);

            if (rawResponse.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No response from JIMI API");
            }

            JsonNode response = objectMapper.readTree(rawResponse);

            int code = response.has("code") ? response.get("code").asInt() : -1;
            if (code != 0) {
                String msg = response.has("message") ? response.get("message").asText()
                        : response.has("msg") ? response.get("msg").asText() : "unknown error";
                log.warn("JIMI API error: code={}, msg={}", code, msg);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "JIMI API error: " + msg);
            }

            return response.get("result");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("JIMI API call failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GPS tracking service is unavailable");
        }
    }

    static RiderLocationResponse.RiderLocationResponseBuilder parseLocationNode(JsonNode node) {
        return RiderLocationResponse.builder()
                .lat(node.has("lat") ? node.get("lat").asDouble() : null)
                .lng(node.has("lng") ? node.get("lng").asDouble() : null)
                .speed(node.has("speed") ? node.get("speed").asDouble() : null)
                .deviceStatus(node.has("deviceStatus") ? node.get("deviceStatus").asText() : null)
                .accStatus(node.has("accStatus") ? node.get("accStatus").asText() : null)
                .gpsTime(node.has("gpsTime") ? node.get("gpsTime").asText() : null)
                .located(node.has("located") && node.get("located").asBoolean());
    }

    static RiderTrackResponse.TrackPoint parseTrackPoint(JsonNode node) {
        return RiderTrackResponse.TrackPoint.builder()
                .lat(node.has("lat") ? node.get("lat").asDouble() : null)
                .lng(node.has("lng") ? node.get("lng").asDouble() : null)
                .speed(node.has("speed") ? node.get("speed").asDouble() : null)
                .gpsTime(node.has("gpsTime") ? node.get("gpsTime").asText() : null)
                .direction(node.has("direction") ? node.get("direction").asDouble() : null)
                .altitude(node.has("altitude") ? node.get("altitude").asDouble() : null)
                .posType(node.has("posType") ? node.get("posType").asInt() : null)
                .build();
    }

    private String buildFormBody(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append("&");
            sb.append(k).append("=").append(v);
        });
        return sb.toString();
    }
}
