package streambuted.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the StreamButed Identity Service.
 *
 * Responsibilities:
 *  - User registration and authentication (REST /api/v1/auth)
 *  - Profile management (REST /api/v1/users)
 *  - JWT issuance and refresh-token rotation
 *  - Intra-service token validation via gRPC (TokenValidator)
 *  - Publishing domain events to RabbitMQ (e.g. UserPromotedEvent)
 */
@SpringBootApplication
@EnableScheduling
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
