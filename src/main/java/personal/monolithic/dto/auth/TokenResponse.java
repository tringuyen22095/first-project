package personal.monolithic.dto.auth;

public record TokenResponse(String accessToken, long accessTokenExpiresIn, String refreshToken, long refreshTokenExpiresIn) {
}
