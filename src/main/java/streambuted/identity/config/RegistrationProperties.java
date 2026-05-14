package streambuted.identity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.registration")
public class RegistrationProperties {

    private Duration verificationCodeTtl = Duration.ofMinutes(15);
}
