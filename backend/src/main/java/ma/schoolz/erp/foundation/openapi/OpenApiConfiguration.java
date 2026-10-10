package ma.schoolz.erp.foundation.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    OpenAPI schoolErpOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("SchoolZ ERP API")
                                .description(
                                        "Tenant-aware API for Moroccan private school operations")
                                .version("v1")
                                .contact(new Contact().name("SchoolZ Engineering"))
                                .license(new License().name("Proprietary")))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description(
                                                        "Short-lived access token returned by"
                                                                + " /api/v1/auth/login or refresh"))
                                .addSecuritySchemes(
                                        "schoolzApiKey",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.APIKEY)
                                                .in(SecurityScheme.In.HEADER)
                                                .name("X-SchoolZ-Api-Key")
                                                .description(
                                                        "Server integration key. The clear value is"
                                                            + " shown once; only its SHA-256 hash"
                                                            + " is stored.")));
    }
}
