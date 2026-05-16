package com.algotrading.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI metadata shown in Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI algotradingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Algo Trading Service API")
                        .version("1.0.0")
                        .description("""
                                REST API for Zerodha Kite authentication, instrument lookup,
                                strategy evaluation, and explicit order placement.
                                Strategy evaluation never places orders automatically.
                                """)
                        .contact(new Contact()
                                .name("Algo Trading Service"))
                        .license(new License()
                                .name("Proprietary")))
                .servers(List.of(new Server()
                        .url("/")
                        .description("Current application host")));
    }
}
