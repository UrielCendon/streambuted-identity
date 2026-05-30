package streambuted.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import streambuted.identity.config.DesktopAuthProperties;
import streambuted.identity.dto.ErrorResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for the Identity Service REST API.
 *
 * Session policy : STATELESS. All state is carried in the JWT.
 * CSRF           : disabled. Not applicable for a stateless REST API.
 * Password hash  : BCrypt with cost factor 12.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final DesktopAuthProperties desktopAuthProperties;

    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String allowedOriginsProperty;

    @Value("${app.desktop-auth.electron-renderer-origin:app://streambuted}")
    private String electronRendererOrigin;

    @Value("${app.desktop-auth.web-allowed-origins:http://localhost:5173,http://localhost}")
    private String desktopAuthWebAllowedOriginsProperty;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    writeJsonError(
                        response,
                        HttpStatus.FORBIDDEN,
                        ErrorResponse.of(
                            "AccessDeniedException",
                            "No tienes permisos para acceder a este recurso.",
                            HttpStatus.FORBIDDEN.value()
                        )
                    )
                )
            )
            .authorizeHttpRequests(auth -> auth
                // Public auth endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register/resend").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register/verify").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register/cancel").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/google").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/google/callback").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/oauth/google/callback").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/desktop/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/desktop/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/desktop/logout").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/desktop/handoff-codes").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/desktop/exchange").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/.well-known/jwks.json").permitAll()
                // Actuator health is public for infrastructure checks.
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Everything else requires a valid JWT
                .anyRequest().authenticated()
            )
            .addFilterBefore(
                new DesktopAuthEnabledFilter(desktopAuthProperties, objectMapper),
                JwtAuthenticationFilter.class
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authenticationException) ->
            writeJsonError(
                response,
                HttpStatus.UNAUTHORIZED,
                ErrorResponse.of(
                    "AuthenticationException",
                    "Falta el token Bearer o no es valido.",
                    HttpStatus.UNAUTHORIZED.value()
                )
            );
    }

    /**
     * BCrypt with cost 12 balances security against registration latency.
     * (~300 ms on a modern server, acceptable for an infrequent operation).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> allowedOrigins = parseExplicitOrigins("cors.allowed-origins", allowedOriginsProperty);
        List<String> desktopAuthWebOrigins = parseExplicitOrigins(
            "app.desktop-auth.web-allowed-origins",
            desktopAuthWebAllowedOriginsProperty
        );

        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOrigins(allowedOrigins);
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        corsConfiguration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        corsConfiguration.setExposedHeaders(List.of("Set-Cookie"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(
            "/api/v1/auth/desktop/handoff-codes",
            desktopHandoffCorsConfiguration(desktopAuthWebOrigins)
        );
        source.registerCorsConfiguration("/api/v1/auth/desktop/login", desktopMainOnlyCorsConfiguration());
        source.registerCorsConfiguration("/api/v1/auth/desktop/refresh", desktopMainOnlyCorsConfiguration());
        source.registerCorsConfiguration("/api/v1/auth/desktop/logout", desktopMainOnlyCorsConfiguration());
        source.registerCorsConfiguration("/api/v1/auth/desktop/exchange", desktopMainOnlyCorsConfiguration());
        source.registerCorsConfiguration("/**", corsConfiguration);
        return source;
    }

    private List<String> parseExplicitOrigins(String propertyName, String propertyValue) {
        List<String> origins = Arrays.stream(propertyValue.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isBlank())
            .toList();

        if (
            origins.isEmpty() ||
            origins.contains("*") ||
            origins.stream().anyMatch(origin -> origin.equalsIgnoreCase("null"))
        ) {
            throw new IllegalStateException(propertyName + " must define explicit origins and cannot include '*' or 'null'.");
        }

        return origins;
    }

    private CorsConfiguration desktopHandoffCorsConfiguration(List<String> webOrigins) {
        List<String> safeWebOrigins = webOrigins.stream()
            .filter(origin -> !origin.equals(electronRendererOrigin))
            .toList();

        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOrigins(safeWebOrigins);
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setAllowedMethods(List.of("POST", "OPTIONS"));
        corsConfiguration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        return corsConfiguration;
    }

    private CorsConfiguration desktopMainOnlyCorsConfiguration() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOrigins(List.of());
        corsConfiguration.setAllowedMethods(List.of("POST", "OPTIONS"));
        corsConfiguration.setAllowedHeaders(List.of("Content-Type"));
        return corsConfiguration;
    }

    private void writeJsonError(
        HttpServletResponse response,
        HttpStatus status,
        ErrorResponse errorResponse
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
