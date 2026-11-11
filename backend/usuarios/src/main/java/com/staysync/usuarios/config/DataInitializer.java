package com.staysync.usuarios.config;

import com.staysync.usuarios.model.Usuario;
import com.staysync.usuarios.repository.UsuarioRepository;
import com.staysync.usuarios.tenant.model.Hotel;
import com.staysync.usuarios.tenant.repository.HotelRepository;
import com.staysync.usuarios.tenant.service.HotelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Datos iniciales del servicio, en cada arranque (idempotente):
 * <ul>
 *   <li>Siempre: el hotel por defecto y la migración de usuarios previos al multi-tenant.</li>
 *   <li>Super admin inicial: solo si se configuran SUPERADMIN_EMAIL y SUPERADMIN_PASSWORD y no hay
 *       ningún super admin activo. Es la forma de tener el primero sin contraseñas versionadas.</li>
 *   <li>Datos demo (segundo hotel y cuentas con contraseñas publicadas en el repositorio): solo con
 *       DEMO_DATA_ENABLED=true. Sin él, las cuentas demo que aún usan su contraseña publicada se
 *       desactivan: en un entorno real cualquiera podría entrar con ellas.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // después de MigracionEsquema
@Slf4j
public class DataInitializer implements ApplicationRunner {

    /** Segundo hotel de prueba, para verificar el aislamiento entre hoteles (Etapa 2). */
    static final String TENANT_ANDES = "hotel-andes";

    /** Misma política que RegistroRequest. */
    private static final Pattern PASSWORD_SEGURA =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,100}$");

    /** tenantId null = hotel por defecto. Las contraseñas son públicas: solo para entornos demo. */
    record CuentaDemo(String tenantId, String nombre, String apellido, String email, String password, Usuario.Rol rol) { }

    private static final String ADMIN = "Admin";
    private static final String ANDES = "Andes";

    static final List<CuentaDemo> CUENTAS_DEMO = List.of(
            new CuentaDemo(null,         "Super",         ADMIN,      "superadmin@staysync.com", "Super@1234",   Usuario.Rol.SUPER_ADMIN),
            new CuentaDemo(null,         ADMIN,           "StaySync", "admin@staysync.com",      "Admin@1234",   Usuario.Rol.ADMIN),
            new CuentaDemo(null,         "Recepcionista", "StaySync", "recep@staysync.com",      "Recep@1234",   Usuario.Rol.RECEPCIONISTA),
            new CuentaDemo(null,         "Huésped",       "Demo",     "huesped@staysync.com",    "Huesped@1234", Usuario.Rol.HUESPED),
            new CuentaDemo(TENANT_ANDES, ADMIN,           ANDES,      "admin@hotelandes.com",    "Admin@1234",   Usuario.Rol.ADMIN),
            new CuentaDemo(TENANT_ANDES, "Recepcionista", ANDES,      "recep@hotelandes.com",    "Recep@1234",   Usuario.Rol.RECEPCIONISTA),
            new CuentaDemo(TENANT_ANDES, "Huésped",       ANDES,      "huesped@hotelandes.com",  "Huesped@1234", Usuario.Rol.HUESPED));

    private final UsuarioRepository usuarioRepository;
    private final HotelRepository   hotelRepository;
    private final HotelService      hotelService;
    private final PasswordEncoder   passwordEncoder;
    private final boolean demoHabilitado;
    private final String  superAdminEmail;
    private final String  superAdminPassword;

    public DataInitializer(UsuarioRepository usuarioRepository,
                           HotelRepository hotelRepository,
                           HotelService hotelService,
                           PasswordEncoder passwordEncoder,
                           @Value("${staysync.demo.habilitado:false}") boolean demoHabilitado,
                           @Value("${staysync.superadmin-inicial.email:}") String superAdminEmail,
                           @Value("${staysync.superadmin-inicial.password:}") String superAdminPassword) {
        this.usuarioRepository  = usuarioRepository;
        this.hotelRepository    = hotelRepository;
        this.hotelService       = hotelService;
        this.passwordEncoder    = passwordEncoder;
        this.demoHabilitado     = demoHabilitado;
        this.superAdminEmail    = superAdminEmail == null ? "" : superAdminEmail.trim();
        this.superAdminPassword = superAdminPassword == null ? "" : superAdminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        validarSuperAdminInicial();
        String tenantPorDefecto = hotelService.getDefaultTenantId();
        // Los registros sin hotelSlug van a este hotel: debe existir también fuera de demo
        seedHotel(tenantPorDefecto, "StaySync Demo",
                "{\"primary\":\"#222016\",\"secondary\":\"#EFC143\",\"background\":\"#FEF8E1\",\"text\":\"#212529\"}");

        int migrados = usuarioRepository.asignarTenantASinHotel(tenantPorDefecto);
        if (migrados > 0) {
            log.info("{} usuario(s) sin hotel asignados a '{}'", migrados, tenantPorDefecto);
        }

        if (demoHabilitado) {
            log.warn("DEMO_DATA_ENABLED=true: cuentas demo con contraseñas públicas activas. No usar en producción.");
            seedHotel(TENANT_ANDES, "Hotel Andes",
                    "{\"primary\":\"#7A2E1F\",\"secondary\":\"#E9B872\",\"background\":\"#FBF8F4\",\"text\":\"#2B211C\"}");
            CUENTAS_DEMO.forEach(c -> seedCuentaDemo(c, tenantPorDefecto));
        } else {
            CUENTAS_DEMO.forEach(this::desactivarSiExpuesta);
        }

        crearSuperAdminInicial(tenantPorDefecto);
    }

    /** Falla al arrancar ante una configuración a medias o débil, en vez de ignorarla en silencio. */
    private void validarSuperAdminInicial() {
        boolean conEmail = !superAdminEmail.isEmpty();
        boolean conPassword = !superAdminPassword.isEmpty();
        if (conEmail != conPassword) {
            throw new IllegalStateException(
                    "Super admin inicial: SUPERADMIN_EMAIL y SUPERADMIN_PASSWORD se configuran juntos (falta uno).");
        }
        if (conPassword && !PASSWORD_SEGURA.matcher(superAdminPassword).matches()) {
            throw new IllegalStateException("Super admin inicial: SUPERADMIN_PASSWORD debe tener entre 8 y 100 "
                    + "caracteres, con mayúscula, minúscula, número y carácter especial (@$!%*?&).");
        }
        if (conPassword && esPasswordDemo(superAdminPassword)) {
            throw new IllegalStateException(
                    "Super admin inicial: SUPERADMIN_PASSWORD no puede ser una de las contraseñas demo publicadas.");
        }
    }

    private void crearSuperAdminInicial(String tenantId) {
        if (usuarioRepository.existsByRolAndActivoTrue(Usuario.Rol.SUPER_ADMIN)) {
            return;
        }
        if (superAdminEmail.isEmpty()) {
            log.warn("No hay ningún super admin activo. Para crear el primero, define SUPERADMIN_EMAIL y "
                    + "SUPERADMIN_PASSWORD y reinicia el servicio (ver docs/seguridad.md).");
            return;
        }
        if (usuarioRepository.existsByEmail(superAdminEmail)) {
            // No se promueve ni se cambia la contraseña de una cuenta existente desde la configuración
            log.warn("Super admin inicial no creado: ya existe un usuario con el email {}.", superAdminEmail);
            return;
        }
        usuarioRepository.save(Usuario.builder()
                .tenantId(tenantId)
                .nombre("Super")
                .apellido(ADMIN)
                .email(superAdminEmail)
                .passwordHash(passwordEncoder.encode(superAdminPassword))
                .rol(Usuario.Rol.SUPER_ADMIN)
                .activo(true)
                .build());
        log.info("Super admin inicial creado: {}. Quita SUPERADMIN_PASSWORD de la configuración.", superAdminEmail);
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
        log.info("Hotel creado: {}", tenantId);
    }

    private void seedCuentaDemo(CuentaDemo c, String tenantPorDefecto) {
        usuarioRepository.findByEmail(c.email()).ifPresentOrElse(u -> {
            // Reactiva la que se desactivó al arrancar sin modo demo, si nadie le cambió la contraseña
            if (!Boolean.TRUE.equals(u.getActivo()) && usaPasswordPublicada(u, c)) {
                u.setActivo(true);
                usuarioRepository.save(u);
                log.info("Usuario demo reactivado: {}", c.email());
            }
        }, () -> {
            usuarioRepository.save(Usuario.builder()
                    .tenantId(c.tenantId() != null ? c.tenantId() : tenantPorDefecto)
                    .nombre(c.nombre())
                    .apellido(c.apellido())
                    .email(c.email())
                    .passwordHash(passwordEncoder.encode(c.password()))
                    .rol(c.rol())
                    .activo(true)
                    .build());
            log.info("Usuario demo creado: {} ({})", c.email(), c.rol());
        });
    }

    private void desactivarSiExpuesta(CuentaDemo c) {
        usuarioRepository.findByEmail(c.email())
                .filter(u -> Boolean.TRUE.equals(u.getActivo()) && usaPasswordPublicada(u, c))
                .ifPresent(u -> {
                    u.setActivo(false);
                    usuarioRepository.save(u);
                    log.warn("Cuenta demo {} ({}) desactivada: su contraseña está publicada en el repositorio. "
                            + "Cámbiala y reactívala, o usa DEMO_DATA_ENABLED=true si es un entorno demo.",
                            c.email(), c.rol());
                });
    }

    private boolean usaPasswordPublicada(Usuario u, CuentaDemo c) {
        return u.getPasswordHash() != null && passwordEncoder.matches(c.password(), u.getPasswordHash());
    }

    private static boolean esPasswordDemo(String password) {
        return CUENTAS_DEMO.stream().anyMatch(c -> c.password().equals(password));
    }
}
