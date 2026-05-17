package shortly.mandmcorp.dev.shortly.service.tracking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.config.JimiConfig;
import shortly.mandmcorp.dev.shortly.model.JimiAccessToken;
import shortly.mandmcorp.dev.shortly.repository.JimiAccessTokenRepository;

@Service
@Slf4j
public class JimiTokenService {

    private static final long DEFAULT_EXPIRES_IN = 7200L;
    private static final long TOKEN_REFRESH_BUFFER_SECONDS = 300;
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JimiConfig jimiConfig;
    private final WebClient webClient;
    private final JimiAccessTokenRepository tokenRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JimiTokenService(JimiConfig jimiConfig, WebClient webClient, JimiAccessTokenRepository tokenRepository) {
        this.jimiConfig = jimiConfig;
        this.webClient = webClient;
        this.tokenRepository = tokenRepository;
    }

    public String getAccessToken() {
        Optional<JimiAccessToken> stored = tokenRepository.findById(JimiAccessToken.SINGLETON_ID);
        if (stored.isPresent() && Instant.now().getEpochSecond() < stored.get().getExpiresAt()) {
            return stored.get().getAccessToken();
        }
        return fetchAndStoreNewToken();
    }

    // synchronized so only one instance races to refresh when the token is stale
    private synchronized String fetchAndStoreNewToken() {
        // Re-read after acquiring lock — another instance may have already refreshed
        Optional<JimiAccessToken> stored = tokenRepository.findById(JimiAccessToken.SINGLETON_ID);
        if (stored.isPresent() && Instant.now().getEpochSecond() < stored.get().getExpiresAt()) {
            return stored.get().getAccessToken();
        }

        String timestamp = getCurrentTimestamp();
        Map<String, String> params = new TreeMap<>();
        params.put("method", "jimi.oauth.token.get");
        params.put("timestamp", timestamp);
        params.put("app_key", jimiConfig.getAppKey());
        params.put("sign_method", "md5");
        params.put("v", "1.0");
        params.put("format", "json");
        params.put("user_id", jimiConfig.getUserId());
        params.put("user_pwd_md5", jimiConfig.getUserPasswordMd5());
        params.put("expires_in", String.valueOf(DEFAULT_EXPIRES_IN));

        String sign = computeSign(params);
        params.put("sign", sign);

        byte[] responseBytes = webClient.post()
                .uri(jimiConfig.getBaseUrl())
                .bodyValue(buildFormBody(params))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .exchangeToMono(response -> response.bodyToMono(byte[].class))
                .block();

        if (responseBytes == null || responseBytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "JIMI token response is empty");
        }

        JsonNode response;
        try {
            response = objectMapper.readTree(new String(responseBytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Failed to parse JIMI token response: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "JIMI token response is not valid JSON");
        }

        if (!response.has("result")) {
            log.warn("JIMI token response (no result): {}", response);
            int code = response.has("code") ? response.get("code").asInt() : -1;
            String msg = response.has("message") ? response.get("message").asText()
                    : response.has("msg") ? response.get("msg").asText() : "unknown error";
            log.warn("JIMI token error: code={}, msg={}", code, msg);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "JIMI token error: " + msg);
        }

        JsonNode result = response.get("result");

        if (!result.has("accessToken")) {
            log.warn("JIMI token result missing accessToken: {}", result);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "JIMI token result missing accessToken");
        }

        String token = result.get("accessToken").asText();
        long expiresIn = result.has("expiresIn") ? result.get("expiresIn").asLong() : DEFAULT_EXPIRES_IN;
        long expiresAt = Instant.now().getEpochSecond() + expiresIn - TOKEN_REFRESH_BUFFER_SECONDS;

        tokenRepository.save(JimiAccessToken.builder()
                .id(JimiAccessToken.SINGLETON_ID)
                .accessToken(token)
                .expiresAt(expiresAt)
                .build());

        log.info("JIMI access token refreshed and stored, expires in {}s", expiresIn);
        return token;
    }

    String computeSign(Map<String, String> params) {
        StringBuilder sb = new StringBuilder(jimiConfig.getAppSecret());
        new TreeMap<>(params).forEach((k, v) -> sb.append(k).append(v));
        sb.append(jimiConfig.getAppSecret());
        return md5Hex(sb.toString()).toUpperCase();
    }

    String getCurrentTimestamp() {
        return LocalDateTime.now(ZoneOffset.UTC).format(TIMESTAMP_FMT);
    }

    private String buildFormBody(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append("&");
            sb.append(k).append("=").append(v);
        });
        return sb.toString();
    }

    private static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }
}
