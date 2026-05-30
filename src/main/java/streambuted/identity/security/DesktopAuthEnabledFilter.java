package streambuted.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import streambuted.identity.config.DesktopAuthProperties;
import streambuted.identity.dto.ErrorResponse;

import java.io.IOException;

public class DesktopAuthEnabledFilter extends OncePerRequestFilter {

    private static final String DESKTOP_AUTH_PATH_PREFIX = "/api/v1/auth/desktop/";

    private final DesktopAuthProperties properties;
    private final ObjectMapper objectMapper;

    public DesktopAuthEnabledFilter(DesktopAuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (isDesktopAuthRequest(request) && !properties.isEnabled()) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                response.getWriter(),
                ErrorResponse.of(
                    "DesktopAuthDisabledException",
                    "La autenticacion desktop no esta disponible.",
                    HttpStatus.NOT_FOUND.value()
                )
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isDesktopAuthRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith(DESKTOP_AUTH_PATH_PREFIX);
    }
}
