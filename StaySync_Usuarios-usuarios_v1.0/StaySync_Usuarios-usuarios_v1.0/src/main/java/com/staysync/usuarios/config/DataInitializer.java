package com.staysync.usuarios.config;

import com.staysync.usuarios.model.Usuario;
import com.staysync.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the three demo accounts on every startup if they don't already exist.
 * Safe to run repeatedly — existsByEmail guards against duplicates.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder   passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        seed("Admin",          "StaySync",  "admin@staysync.com",  "Admin@1234",   Usuario.Rol.ADMIN);
        seed("Recepcionista",  "StaySync",  "recep@staysync.com",  "Recep@1234",   Usuario.Rol.RECEPCIONISTA);
        seed("Huésped",        "Demo",      "huesped@staysync.com","Huesped@1234", Usuario.Rol.HUESPED);
    }

    private void seed(String nombre, String apellido, String email, String password, Usuario.Rol rol) {
        if (usuarioRepository.existsByEmail(email)) {
            return;
        }
        Usuario u = Usuario.builder()
                .nombre(nombre)
                .apellido(apellido)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .rol(rol)
                .activo(true)
                .build();
        usuarioRepository.save(u);
        log.info("Usuario demo creado: {} ({})", email, rol);
    }
}
