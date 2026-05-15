package streambuted.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Validates the Bearer JWT on every incoming request and populates the
 * SecurityContext when the token is valid.
 *
 * Requests without a valid token are passed through without error.
 * endpoint-level authorization rules decide whether they are allowed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX        = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest  request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        jwtService.extractClaims(token).ifPresent(claims -> {
            // Skip if SecurityContext is already populated (e.g. by a previous filter)
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String role = claims.get("role", String.class);
                UUID   userId = UUID.fromString(claims.getSubject());

                var authority = new SimpleGrantedAuthority("ROLE_" + role.toUpperCase());
                var authentication = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(authority)
                );
                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated user {} with role {}", userId, role);
            }
        });

        filterChain.doFilter(request, response);
    }
}
