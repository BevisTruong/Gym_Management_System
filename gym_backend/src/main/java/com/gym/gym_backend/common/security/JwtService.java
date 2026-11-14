package com.gym.gym_backend.common.security;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class JwtService {
    private final ObjectMapper mapper;
    private final byte[] key;
    private final long lifetimeSeconds;
    private final SecureRandom random = new SecureRandom();

    public JwtService(ObjectMapper mapper, @Value("${app.security.jwt-secret:}") String configuredSecret,
            @Value("${app.security.jwt-expiration-seconds:28800}") long lifetimeSeconds) {
        this.mapper = mapper;
        if (configuredSecret == null || configuredSecret.isBlank()) {
            this.key = new byte[32];
            random.nextBytes(this.key);
        } else {
            this.key = configuredSecret.getBytes(StandardCharsets.UTF_8);
            if (this.key.length < 32)
                throw new IllegalStateException("APP_JWT_SECRET must contain at least 32 bytes");
        }
        this.lifetimeSeconds = lifetimeSeconds;
    }

    public String issue(CurrentUser user, String passwordHash) {
        try {
            String header = encode(mapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            Map<String, Object> claims = new java.util.HashMap<>();
            claims.put("sub", user.accountId());
            claims.put("username", user.username());
            claims.put("fullName", user.fullName());
            claims.put("role", user.role().name());
            claims.put("memberId", user.memberId());
            claims.put("trainerId", user.trainerId());
            claims.put("mustChangePassword", user.mustChangePassword());
            claims.put("credential", credentialFingerprint(passwordHash));
            claims.put("exp", Instant.now().getEpochSecond() + lifetimeSeconds);
            String payload = encode(mapper.writeValueAsBytes(claims));
            String unsigned = header + "." + payload;
            return unsigned + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(unsigned));
        } catch (Exception e) {
            throw new IllegalStateException("Could not issue access token", e);
        }
    }

    public boolean matchesCredential(String token, String passwordHash) {
        try {
            String[] parts = token.split("\\.");
            Map<?, ?> claims = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            Object credential = claims.get("credential");
            return credential instanceof String value && java.security.MessageDigest.isEqual(
                    value.getBytes(StandardCharsets.US_ASCII),
                    credentialFingerprint(passwordHash).getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            return false;
        }
    }

    private String credentialFingerprint(String passwordHash) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sign("credential:" + passwordHash));
    }

    public CurrentUser verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !java.security.MessageDigest.isEqual(sign(parts[0] + "." + parts[1]),
                    Base64.getUrlDecoder().decode(parts[2])))
                return null;
            Map<?, ?> claims = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            if (((Number) claims.get("exp")).longValue() <= Instant.now().getEpochSecond())
                return null;
            return new CurrentUser(((Number) claims.get("sub")).longValue(), (String) claims.get("username"),
                    (String) claims.get("fullName"), UserRole.valueOf((String) claims.get("role")),
                    claims.get("memberId") == null ? null : ((Number) claims.get("memberId")).longValue(),
                    claims.get("trainerId") == null ? null : ((Number) claims.get("trainerId")).longValue(),
                    Boolean.TRUE.equals(claims.get("mustChangePassword")));
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
