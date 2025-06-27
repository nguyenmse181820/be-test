package com.boeing.loyalty.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
public class SwaggerConfig implements WebMvcConfigurer {

    @Bean
    public OpenAPI loyaltyServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Boeing Loyalty Service API")
                        .description("API documentation for Boeing Loyalty Service")
                        .version("1.0")
                        .contact(new Contact()
                                .name("Boeing Team")
                                .email("support@boeing.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("http://www.apache.org/licenses/LICENSE-2.0.html")))
                .addSecurityItem(new SecurityRequirement().addList("Boeing Loyalty Service"))
                .components(new Components().addSecuritySchemes("Boeing Loyalty Service", new SecurityScheme()
                        .name("Boeing - Loyalty Service").type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
} 