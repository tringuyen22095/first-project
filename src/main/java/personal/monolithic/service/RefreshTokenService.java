package personal.monolithic.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import personal.monolithic.dao.RefreshTokenDao;
import personal.monolithic.entity.RefreshToken;
import personal.monolithic.entity.User;

@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenDao refreshTokenDao;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public RefreshToken rotate(User user, long refreshTokenExpirationSeconds) {
        this.refreshTokenDao.deleteByUser(user);

        Instant now = Instant.now();
        RefreshToken refreshToken = RefreshToken.builder()
                .token(generateTokenValue())
                .user(user)
                .createdAt(now)
                .expiresAt(now.plusSeconds(refreshTokenExpirationSeconds))
                .build();

        this.refreshTokenDao.save(refreshToken);
        log.info("Refresh token rotated username={} expiresAt={}", user.getUsr(), refreshToken.getExpiresAt());
        return refreshToken;
    }

    public RefreshToken consume(String token) {
        RefreshToken refreshToken = this.refreshTokenDao.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            this.refreshTokenDao.delete(refreshToken);
            log.warn("Refresh token expired username={}", refreshToken.getUser().getUsr());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        this.refreshTokenDao.delete(refreshToken);
        log.info("Refresh token consumed username={}", refreshToken.getUser().getUsr());
        return refreshToken;
    }

    private String generateTokenValue() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
