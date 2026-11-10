package com.staysync.usuarios.config;

import com.staysync.usuarios.model.Usuario;
import com.staysync.usuarios.repository.UsuarioRepository;
import com.staysync.usuarios.tenant.model.Hotel;
import com.staysync.usuarios.tenant.repository.HotelRepository;
import com.staysync.usuarios.tenant.service.HotelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DataInitializer - datos demo solo con opt-in y super admin inicial")
class DataInitializerTest {

    private static final String DEFAULT = "staysync-demo";

    @Mock UsuarioRepository usuarios;
    @Mock HotelRepository hoteles;
    @Mock HotelService hotelService;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @BeforeEach
    void setUp() {
        when(hotelService.getDefaultTenantId()).thenReturn(DEFAULT);
        when(usuarios.findByEmail(anyString())).thenReturn(Optional.empty());
    }

    private DataInitializer inicializador(boolean demo, String email, String password) {
        return new DataInitializer(usuarios, hoteles, hotelService, encoder, demo, email, password);
    }

    private Usuario cuenta(String email, String password, boolean activo) {
        return Usuario.builder().id(1L).tenantId(DEFAULT).nombre("X").apellido("Y").email(email)
                .passwordHash(encoder.encode(password)).rol(Usuario.Rol.SUPER_ADMIN).activo(activo).build();
    }

    @Test
    @DisplayName("Sin modo demo crea solo el hotel por defecto y ninguna cuenta")
    void sinDemoNoCreaCuentas() {
        inicializador(false, "", "").run(null);

        ArgumentCaptor<Hotel> hotel = ArgumentCaptor.forClass(Hotel.class);
        verify(hoteles).save(hotel.capture());
        assertThat(hotel.getValue().getTenantId()).isEqualTo(DEFAULT);
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("Sin modo demo desactiva las cuentas demo que aún usan su contraseña publicada")
    void sinDemoDesactivaCuentasExpuestas() {
        Usuario expuesta = cuenta("superadmin@staysync.com", "Super@1234", true);
        Usuario cambiada = cuenta("admin@staysync.com", "OtraClave@99", true);
        when(usuarios.findByEmail("superadmin@staysync.com")).thenReturn(Optional.of(expuesta));
        when(usuarios.findByEmail("admin@staysync.com")).thenReturn(Optional.of(cambiada));

        inicializador(false, "", "").run(null);

        assertThat(expuesta.getActivo()).isFalse();
        assertThat(cambiada.getActivo()).isTrue();
        verify(usuarios).save(expuesta);
        verify(usuarios, never()).save(cambiada);
    }

    @Test
    @DisplayName("Con modo demo crea los dos hoteles y las siete cuentas demo")
    void conDemoCreaTodo() {
        inicializador(true, "", "").run(null);

        verify(hoteles, times(2)).save(any(Hotel.class));
        ArgumentCaptor<Usuario> creados = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios, times(DataInitializer.CUENTAS_DEMO.size())).save(creados.capture());
        assertThat(creados.getAllValues())
                .anySatisfy(u -> {
                    assertThat(u.getEmail()).isEqualTo("admin@hotelandes.com");
                    assertThat(u.getTenantId()).isEqualTo(DataInitializer.TENANT_ANDES);
                })
                .allSatisfy(u -> assertThat(u.getPasswordHash()).startsWith("$2"));
    }

    @Test
    @DisplayName("Con modo demo reactiva una cuenta demo desactivada que conserva su contraseña")
    void conDemoReactiva() {
        Usuario desactivada = cuenta("superadmin@staysync.com", "Super@1234", false);
        when(usuarios.findByEmail("superadmin@staysync.com")).thenReturn(Optional.of(desactivada));

        inicializador(true, "", "").run(null);

        assertThat(desactivada.getActivo()).isTrue();
    }

    @Test
    @DisplayName("Crea el super admin inicial configurado si no hay ninguno activo")
    void creaSuperAdminInicial() {
        when(usuarios.existsByRolAndActivoTrue(Usuario.Rol.SUPER_ADMIN)).thenReturn(false);

        inicializador(false, "jefa@hotel.cl", "Fuerte!2026x").run(null);

        ArgumentCaptor<Usuario> creado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(creado.capture());
        assertThat(creado.getValue().getEmail()).isEqualTo("jefa@hotel.cl");
        assertThat(creado.getValue().getRol()).isEqualTo(Usuario.Rol.SUPER_ADMIN);
        assertThat(creado.getValue().getTenantId()).isEqualTo(DEFAULT);
        assertThat(encoder.matches("Fuerte!2026x", creado.getValue().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("No crea el super admin inicial si ya hay uno activo o si el email ya existe")
    void noDuplicaSuperAdmin() {
        when(usuarios.existsByRolAndActivoTrue(Usuario.Rol.SUPER_ADMIN)).thenReturn(true);
        inicializador(false, "jefa@hotel.cl", "Fuerte!2026x").run(null);

        when(usuarios.existsByRolAndActivoTrue(Usuario.Rol.SUPER_ADMIN)).thenReturn(false);
        when(usuarios.existsByEmail("jefa@hotel.cl")).thenReturn(true);
        inicializador(false, "jefa@hotel.cl", "Fuerte!2026x").run(null);

        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("No arranca con un super admin inicial a medias, débil o con una contraseña demo")
    void rechazaConfiguracionInvalida() {
        assertThatThrownBy(() -> inicializador(false, "jefa@hotel.cl", "").run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SUPERADMIN_PASSWORD");
        assertThatThrownBy(() -> inicializador(false, "", "Fuerte!2026x").run(null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> inicializador(false, "jefa@hotel.cl", "debil").run(null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> inicializador(false, "jefa@hotel.cl", "Super@1234").run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("demo");

        verify(usuarios, never()).save(any());
    }
}
