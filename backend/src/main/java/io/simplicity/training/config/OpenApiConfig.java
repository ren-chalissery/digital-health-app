package io.simplicity.training.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the API for the three generated clients.
 *
 * <p>The version is fixed rather than taken from the build, because the document is committed and
 * diffed: a version that moved with every release would make the drift check fail on releases that
 * changed nothing.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI apiDefinition() {
    return new OpenAPI()
        // A relative server, so the document does not record whichever host generated it and each
        // client supplies its own base URL per environment.
        .servers(List.of(new Server().url("/")))
        .info(
            new Info()
                .title("Simplicity training API")
                .version("1")
                .description(
                    "Backend for the Simplicity digital training package. Every endpoint except "
                        + "the invitation preview requires a Cognito access token."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Cognito access token")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
  }
}
