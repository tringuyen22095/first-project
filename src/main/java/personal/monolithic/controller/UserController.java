package personal.monolithic.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import personal.monolithic.dto.user.CurrentUserResponse;
import personal.monolithic.service.UserService;

@Tag(name = "User")
@RestController
@RequestMapping("/user")
@Slf4j
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get current logged-in user information")
    @GetMapping("/me")
    @PreAuthorize("hasAuthority('USER_ROLE')")
    @ResponseStatus(HttpStatus.OK)
    public CurrentUserResponse getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        log.info("Current user endpoint invoked username={}", jwt.getSubject());
        return this.userService.getCurrentUser(jwt.getSubject());
    }
}
