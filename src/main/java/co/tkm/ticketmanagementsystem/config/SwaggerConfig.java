package co.tkm.ticketmanagementsystem.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_SCHEME = "bearer";

    /**
     * Declaring the bearer scheme puts an Authorize button in Swagger UI: paste the
     * access_token that POST /login returned and every "try it out" call is sent with
     * an Authorization header, which is the only way to reach a secured endpoint now
     * that the app no longer accepts HTTP Basic or a session cookie.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ticket Management System API")
                        .version("1.0")
                        .description("API documentation for the Ticket Management System. "
                                + "Authentication: POST /login (or /api/auth/login) "
                                + "returns an access_token to send as 'Bearer <token>'."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token from POST /login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
