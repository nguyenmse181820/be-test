package com.boeing.aircraftservice.configurations;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Value("${swagger.dev.url}")
    private String swaggerDevUrl;
    @Value("${swagger.app.name}")
    private String swaggerAppName;
    @Value("${swagger.add.security.name}")
    private String swaggerAddSecurityName;
    @Value("${swagger.scheme}")
    private String swaggerScheme;
    @Value("${swagger.format}")
    private String swaggerFormat;

    @Bean
    public OpenAPI customOpenAPI() {

        Contact contact = new Contact();
        contact.setEmail("hoangsonhadev@gmail.com");
        contact.setName("HoangSonHa");

        Info info = new Info()
                .title(swaggerAppName)
                .version("3.0")
                .contact(contact)
                .description("Those api below to manage airCraftService");

        SecurityRequirement securityRequirement = new SecurityRequirement();
        securityRequirement.addList(swaggerAddSecurityName);

        Components components = new Components();
        components.addSecuritySchemes(swaggerAddSecurityName, new SecurityScheme().name(swaggerAddSecurityName).type(SecurityScheme.Type.HTTP).scheme(swaggerScheme).bearerFormat(swaggerFormat));

        return new OpenAPI()
                .info(info)
                .addSecurityItem(securityRequirement)
                .components(components);
    }

}