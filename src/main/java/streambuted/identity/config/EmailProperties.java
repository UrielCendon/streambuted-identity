package streambuted.identity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {

    private String from = "no-reply-streambuted@example.com";
    private String verificationSubject = "StreamButed verification code";
}
