package com.staysync.bff.config;

import com.staysync.bff.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    private static final String[] PUBLIC_PATHS = {
            "/bff/auth/login",
            "/bff/auth/registro",
            "/bff/auth/refresh",
            "/bff/habitaciones/disponibles",
            "/bff/pagos/stripe/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/actuator/health"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Return 401 (not 403) for unauthenticated requests so the frontend
                // can trigger the refresh-token flow in apiClient.js
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Perfil propio: accesible para cualquier rol autenticado (HUESPED incluido)
                        .requestMatchers(HttpMethod.GET, "/bff/usuarios/perfil").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/bff/usuarios/perfil").authenticated()
                        // Admin + Recepcionista routes (also enforced by @PreAuthorize)
                        .requestMatchers("/bff/habitaciones/**").hasAnyRole("ADMIN", "RECEPCIONISTA")
                        .requestMatchers("/bff/dashboard/**").hasAnyRole("ADMIN", "RECEPCIONISTA")
                        .requestMatchers("/bff/usuarios/**").hasAnyRole("ADMIN", "RECEPCIONISTA")
                        // All remaining routes require authentication
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
