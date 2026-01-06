import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server

// Place your Spring DSL code here
beans = {
    openApi(OpenAPI) { bean ->
        servers = [new Server().url("https://myserver.io/api").description("My Server API")]
        SecurityScheme apiKeyScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .name("bearer")
                .bearerFormat("JWT")
                .scheme("bearer")
                .in(SecurityScheme.In.HEADER)
                .description("Bearer auth")
        components = new Components().addSecuritySchemes("bearer", apiKeyScheme)
        security = [new SecurityRequirement().addList("bearer")]
        info = new Info().title("My API title").version("0.5")
        openapi = "3.0.1"
    }

}