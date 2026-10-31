package com.staysync.bff.config;

import com.staysync.bff.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
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

    private static final String ADMIN         = "ADMIN";
    private static final String RECEPCIONISTA = "RECEPCIONISTA";

    private final JwtAuthFilter jwtAuthFilter;

    private static final String[] PUBLIC_PATHS = {
            "/bff/auth/login",
            "/bff/auth/registro",
            "/bff/auth/refresh",
            "/bff/auth/google",
            "/bff/habitaciones/disponibles",
            "/bff/public/**",
            // Vuelta desde Stripe: verifica la sesión contra Stripe, no confía en el navegador.
            // El checkout no es público: necesita la sesión para saber qué reserva y monto cobrar.
            "/bff/pagos/stripe/confirmar",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/actuator/health"
    };

    /**
     * SUPER_ADMIN (operador de la plataforma) hereda todos los permisos de ADMIN, tanto en
     * las reglas de URL como en @PreAuthorize. El alcance por hotel lo aplica usuarios-service.
     */
    @Bean
    public static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_SUPER_ADMIN > ROLE_ADMIN");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Return 401 (not 403) for unauthenticated requests so the frontend
                // can trigger the refresh-token flow in apiClient.js
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        // 403 escrito aquí mismo: con sendError Spring reenvía a /error, que no es
                        // pública, y el usuario recibía 401 (el frontend lo tomaba como sesión vencida)
                        .accessDeniedHandler((request, response, e) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"status\":403,\"message\":\"No tienes permisos para esta operación.\"}");
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Perfil propio: accesible para cualquier rol autenticado (HUESPED incluido)
                        .requestMatchers(HttpMethod.GET, "/bff/usuarios/perfil").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/bff/usuarios/perfil").authenticated()
                        // Operación del hotel: el huésped no lista ni cambia reservas, pagos o tareas
                        // ajenas. Lo suyo (mis reservas, cancelar, pagar) lo controla AccesoHuesped.
                        .requestMatchers(HttpMethod.GET, "/bff/reservas", "/bff/reservas/hoy", "/bff/reservas/*/detalle")
                                .hasAnyRole(ADMIN, RECEPCIONISTA)
                        .requestMatchers(HttpMethod.PATCH, "/bff/reservas/*/estado", "/bff/reservas/*/modificar")
                                .hasAnyRole(ADMIN, RECEPCIONISTA)
                        .requestMatchers(HttpMethod.GET, "/bff/reservas/rango").hasAnyRole(ADMIN, RECEPCIONISTA)
                        .requestMatchers(HttpMethod.POST, "/bff/reservas/*/cuenta/pago").hasAnyRole(ADMIN, RECEPCIONISTA)
                        // Fotos: solo quien administra el hotel
                        .requestMatchers(HttpMethod.POST, "/bff/media").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.GET, "/bff/pagos/**").hasAnyRole(ADMIN, RECEPCIONISTA)
                        .requestMatchers(HttpMethod.POST, "/bff/pagos/*/reembolso").hasAnyRole(ADMIN, RECEPCIONISTA)
                        // Pago en recepción (efectivo, POS): queda COMPLETADO al instante. El huésped paga con Stripe.
                        .requestMatchers(HttpMethod.POST, "/bff/pagos").hasAnyRole(ADMIN, RECEPCIONISTA)
                        .requestMatchers("/bff/servicios/solicitudes/todas", "/bff/servicios/aseo/**")
                                .hasAnyRole(ADMIN, RECEPCIONISTA)
                        .requestMatchers(HttpMethod.PATCH, "/bff/servicios/solicitudes/*/estado")
                                .hasAnyRole(ADMIN, RECEPCIONISTA)
                        // Admin + Recepcionista routes (also enforced by @PreAuthorize)
                        .requestMatchers("/bff/habitaciones/**").hasAnyRole(ADMIN, RECEPCIONISTA)
                        .requestMatchers("/bff/dashboard/**").hasAnyRole(ADMIN, RECEPCIONISTA)
                        .requestMatchers("/bff/usuarios/**").hasAnyRole(ADMIN, RECEPCIONISTA)
                        // All remaining routes require authentication
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
