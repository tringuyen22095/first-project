package personal.monolithic.config;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.AntPathMatcher;

import lombok.RequiredArgsConstructor;
import personal.monolithic.constants.PermissionEnum;
import personal.monolithic.dao.UserDao;
import personal.monolithic.entity.Permission;
import personal.monolithic.entity.User;
import personal.monolithic.security.JwtService;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/sign-in", "/refresh-token", "/health-check",
            "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/error"
    };

    private final UserDao userDao;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth -> auth.requestMatchers(PUBLIC_PATHS).permitAll().anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(publicPathAwareBearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Returns null for public paths so BearerTokenAuthenticationFilter skips
     * token validation entirely — preventing expired-token errors on permitAll endpoints.
     */
    @Bean
    public BearerTokenResolver publicPathAwareBearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        AntPathMatcher matcher = new AntPathMatcher();
        return request -> {
            String path = request.getServletPath();
            boolean isPublic = Arrays.stream(PUBLIC_PATHS).anyMatch(pattern -> matcher.match(pattern, path));
            return isPublic ? null : delegate.resolve(request);
        };
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${security.jwt.secret}") String secret) {
        return NimbusJwtDecoder.withSecretKey(JwtService.buildKey(secret)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(jwtAuthoritiesConverter());
        return authenticationConverter;
    }

    @Bean
    public Converter<Jwt, Collection<GrantedAuthority>> jwtAuthoritiesConverter() {
        return jwt -> {
            final String usr = jwt.getSubject();
            List<GrantedAuthority> authorities = new ArrayList<>();

            final User user = this.userDao.findByUsr(usr);
            List<String> roles = user.getRoles().stream().map(role -> {
                String r = role.getRole();
                String p = String.join("",
                        role.getPermissions().stream().map(Permission::getPermission).map(PermissionEnum::name).sorted().toList());
                return (r + "_" + p).toUpperCase();
            }).toList();

            if (CollectionUtils.isNotEmpty(roles)) {
                roles.stream().map(SimpleGrantedAuthority::new).forEach(authorities::add);
            }

            return authorities;
        };
    }
}
