package personal.monolithic.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import lombok.Getter;
import personal.monolithic.entity.User;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;

    @Getter
    private final long accessTokenExpirationSeconds;

    @Getter
    private final long refreshTokenExpirationSeconds;

    public JwtService(@Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.access-token-expiration-seconds:86400}") long accessTokenExpirationSeconds,
            @Value("${security.jwt.refresh-token-expiration-seconds:2592000}") long refreshTokenExpirationSeconds) {
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(buildKey(secret)));
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
        this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getUsr())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(this.accessTokenExpirationSeconds))
                .claim("uid", user.getId().toString())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return this.jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public static SecretKey buildKey(String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("security.jwt.secret must be at least 32 bytes for HS256");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}
