package tr.edu.mersin.bapadvisor.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import tr.edu.mersin.bapadvisor.config.AppProperties;
import tr.edu.mersin.bapadvisor.user.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final Duration accessTokenValidity;
    private final Duration refreshTokenValidity;

    public JwtTokenProvider(AppProperties appProperties) {
        String secret = appProperties.getJwt().getSecret();
        // Ensure secret is at least 32 bytes (256 bits)
        if (secret.length() < 32) {
            secret = (secret + "01234567890123456789012345678901").substring(0, 32);
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidity = Duration.ofHours(appProperties.getJwt().getExpirationHours());
        this.refreshTokenValidity = Duration.ofDays(appProperties.getJwt().getRefreshExpirationDays());
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenValidity);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("fullName", user.getFullName())
                .claim("type", "ACCESS")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(refreshTokenValidity);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("type", "REFRESH")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }

    public String getEmailFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public UUID getUserIdFromToken(String token) {
        String userIdStr = getClaims(token).get("userId", String.class);
        return userIdStr != null ? UUID.fromString(userIdStr) : null;
    }

    public String getRoleFromToken(String token) {
        return getClaims(token).get("role", String.class);
    }

    public long getAccessTokenExpiresInSeconds() {
        return accessTokenValidity.toSeconds();
    }
}
