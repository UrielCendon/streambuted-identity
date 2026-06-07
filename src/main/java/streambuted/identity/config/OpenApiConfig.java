package streambuted.identity.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI identityOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("StreamButed Identity Service")
                .version("1.0.0")
                .description(
                    "Autenticacion, registro, refresh tokens, perfiles de usuario, OAuth Google, " +
                    "flujo desktop y administracion de cuentas."
                ))
            .servers(List.of(new Server()
                .url("/")
                .description("Gateway o host actual")))
            .components(new Components()
                .addSecuritySchemes("BearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Access token emitido por identity-service.")));
    }
}
