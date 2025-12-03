package shortly.mandmcorp.dev.shortly.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import shortly.mandmcorp.dev.shortly.model.User;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JWTConfig {

    @Value("${JWT_ACCESS_KEY}")
    private String accessKey;
    
    @Value("${JWT_REFRESH_KEY}")
    private String refreshKey;
    
    @Value("${JWT_EXPIRATION_TIME}")
    private long expirationTime;
    
    private Key getSigningKey(String key) {
        return Keys.hmacShaKeyFor(key.getBytes());
    }
    
    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("name", user.getName());
        claims.put("email", user.getEmail());
        return createToken(claims, user.getPhoneNumber(), accessKey, expirationTime);
    }
    
    public String generateRefreshToken(User user) {
        return createToken(new HashMap<>(), user.getPhoneNumber(), refreshKey, expirationTime * 7); // 7 days
    }
    
    private String createToken(Map<String, Object> claims, String subject, String key, long expiration) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(key), SignatureAlgorithm.HS256)
                .compact();
    }
    
    public String extractPhoneNumber(String token) {
        return extractClaim(token, Claims::getSubject, accessKey);
    }
    
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class), accessKey);
    }
    
    public String extractName(String token) {
        return extractClaim(token, claims -> claims.get("name", String.class), accessKey);
    }
    
    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class), accessKey);
    }
    
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration, accessKey);
    }
    
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver, String key) {
        final Claims claims = extractAllClaims(token, key);
        return claimsResolver.apply(claims);
    }
    
    private Claims extractAllClaims(String token, String key) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey(key))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
    
    public boolean validateToken(String token, String phoneNumber) {
        final String extractedPhoneNumber = extractPhoneNumber(token);
        return (extractedPhoneNumber.equals(phoneNumber) && !isTokenExpired(token));
    }
    
    public boolean validateRefreshToken(String token, String phoneNumber) {
        try {
            final Claims claims = extractAllClaims(token, refreshKey);
            return claims.getSubject().equals(phoneNumber) && !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}