package com.staysync.usuarios.config;

import com.staysync.usuarios.model.Usuario;
import com.staysync.usuarios.repository.UsuarioRepository;
import com.staysync.usuarios.tenant.model.Hotel;
import com.staysync.usuarios.tenant.repository.HotelRepository;
import com.staysync.usuarios.tenant.service.HotelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the demo hotels and accounts on every startup if they don't already exist.
 * Safe to run repeatedly — existsById / existsByEmail guard against duplicates.
 * Also migrates users created before multi-tenant support to the default hotel.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // después de MigracionEsquema
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    /** Segundo hotel de prueba, para verificar el aislamiento entre hoteles (Etapa 2). */
    private static final String TENANT_ANDES = "hotel-andes";

    private final UsuarioRepository usuarioRepository;
    private final HotelRepository   hotelRepository;
    private final HotelService      hotelService;
    private final PasswordEncoder   passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        String tenantDemo = hotelService.getDefaultTenantId();
        seedHotel(tenantDemo, "StaySync Demo",
                "{\"primary\":\"#1F4E79\",\"secondary\":\"#F2A900\",\"background\":\"#F7F9FC\",\"text\":\"#1A2433\"}");
        seedHotel(TENANT_ANDES, "Hotel Andes",
                "{\"primary\":\"#7A2E1F\",\"secondary\":\"#2F6B4F\",\"background\":\"#FBF8F4\",\"text\":\"#2B211C\"}");

        int migrados = usuarioRepository.asignarTenantASinHotel(tenantDemo);
        if (migrados > 0) {
            log.info("{} usuario(s) sin hotel asignados a '{}'", migrados, tenantDemo);
        }

        seed(tenantDemo,   "Super",         "Admin",    "superadmin@staysync.com", "Super@1234", Usuario.Rol.SUPER_ADMIN);
        seed(tenantDemo,   "Admin",         "StaySync", "admin@staysync.com",   "Admin@1234",   Usuario.Rol.ADMIN);
        seed(tenantDemo,   "Recepcionista", "StaySync", "recep@staysync.com",   "Recep@1234",   Usuario.Rol.RECEPCIONISTA);
        seed(tenantDemo,   "Huésped",       "Demo",     "huesped@staysync.com", "Huesped@1234", Usuario.Rol.HUESPED);
        seed(TENANT_ANDES, "Admin",         "Andes",    "admin@hotelandes.com", "Admin@1234",   Usuario.Rol.ADMIN);
    }

    private void seedHotel(String tenantId, String nombre, String theme) {
        if (hotelRepository.existsById(tenantId)) {
            return;
        }
        hotelRepository.save(Hotel.builder()
                .tenantId(tenantId)
                .slug(tenantId)
                .nombre(nombre)
                .theme(theme)
                .activo(true)
                .build());
        log.info("Hotel demo creado: {}", tenantId);
    }

    private void seed(String tenantId, String nombre, String apellido, String email, String password, Usuario.Rol rol) {
        if (usuarioRepository.existsByEmail(email)) {
            return;
        }
        Usuario u = Usuario.builder()
                .tenantId(tenantId)
                .nombre(nombre)
                .apellido(apellido)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .rol(rol)
                .activo(true)
                .build();
        usuarioRepository.save(u);
        log.info("Usuario demo creado: {} ({}, {})", email, rol, tenantId);
    }
}
