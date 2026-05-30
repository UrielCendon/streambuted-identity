package streambuted.identity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.desktop-auth")
public class DesktopAuthProperties {

    private boolean enabled = false;

    private long codeTtlSeconds = 300;

    private String allowedRedirectUri = "streambuted://auth/callback";

    private String electronRendererOrigin = "app://streambuted";
}
