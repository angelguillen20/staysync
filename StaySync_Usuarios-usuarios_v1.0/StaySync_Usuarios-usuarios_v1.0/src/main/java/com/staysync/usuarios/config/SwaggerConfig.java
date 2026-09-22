package com.staysync.usuarios.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.*;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title       = "StaySync — Usuarios Service",
        version     = "1.0.0",
        description = "API REST para gestión de usuarios y autenticación JWT en la plataforma StaySync",
        contact     = @Contact(name = "StaySync Dev Team", email = "dev@staysync.com")
    ),
    servers = {
        @Server(url = "http://localhost:8081", description = "Desarrollo local"),
    },
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    name        = "bearerAuth",
    type        = SecuritySchemeType.HTTP,
    scheme      = "bearer",
    bearerFormat= "JWT",
    description = "Ingrese el token JWT obtenido en /api/v1/auth/login"
)
public class SwaggerConfig {}
