package personal.monolithic.service;

import static personal.monolithic.constants.BundleKeys.UNAUTHORIZE;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import personal.monolithic.dao.UserDao;
import personal.monolithic.dto.auth.LoginRequest;
import personal.monolithic.dto.auth.TokenResponse;
import personal.monolithic.entity.RefreshToken;
import personal.monolithic.entity.User;
import personal.monolithic.exception.UnauthorizeException;
import personal.monolithic.security.JwtService;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public TokenResponse signIn(LoginRequest request) {
        log.info("Sign-in requested username={}", request.username());
        final User user = this.userDao.findByUsr(request.username());

        if (!this.passwordEncoder.matches(request.password(), user.getPwd())) {
            log.warn("Sign-in failed because credentials are invalid username={}", request.username());
            throw new UnauthorizeException(UNAUTHORIZE);
        }

        String accessToken = this.jwtService.generateAccessToken(user);
        RefreshToken refreshToken = this.refreshTokenService.rotate(user, this.jwtService.getRefreshTokenExpirationSeconds());
        log.info("Sign-in succeeded username={}", user.getUsr());

        return new TokenResponse(accessToken, this.jwtService.getAccessTokenExpirationSeconds(), refreshToken.getToken(),
                this.jwtService.getRefreshTokenExpirationSeconds());
    }

    public TokenResponse refreshToken(String refreshToken) {
        log.info("Refresh token requested");
        RefreshToken storedRefreshToken = this.refreshTokenService.consume(refreshToken);
        User user = this.userDao.findByUsr(storedRefreshToken.getUser().getUsr());

        String accessToken = this.jwtService.generateAccessToken(user);
        RefreshToken rotatedRefreshToken =
                this.refreshTokenService.rotate(user, this.jwtService.getRefreshTokenExpirationSeconds());
        log.info("Refresh token succeeded username={}", user.getUsr());

        return new TokenResponse(accessToken, this.jwtService.getAccessTokenExpirationSeconds(), rotatedRefreshToken.getToken(),
                this.jwtService.getRefreshTokenExpirationSeconds());
    }
}
