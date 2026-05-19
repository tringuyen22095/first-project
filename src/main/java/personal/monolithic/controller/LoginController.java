package personal.monolithic.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import personal.monolithic.dto.auth.LoginRequest;
import personal.monolithic.dto.auth.TokenResponse;
import personal.monolithic.service.AuthService;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/")
@Slf4j
@RequiredArgsConstructor
public class LoginController {

    private final AuthService authService;

    @Operation(summary = "Health check")
    @GetMapping("/health-check")
    @ResponseStatus(HttpStatus.OK)
    public String healthCheck() {
        log.debug("Health check invoked");
        return "Success";
    }

    @Operation(summary = "Sign in with username or email and password")
    @PostMapping("/sign-in")
    public TokenResponse signIn(@Valid @RequestBody LoginRequest request) {
        log.info("Received sign-in request username={}", request.username());
        return this.authService.signIn(request);
    }

    @Operation(summary = "Refresh access token")
    @PostMapping("/refresh-token")
    public TokenResponse refreshToken(@RequestParam String refreshToken) {
        log.info("Received refresh token request");
        return this.authService.refreshToken(refreshToken);
    }
}
