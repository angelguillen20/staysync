package com.staysync.usuarios.tenant.service;

import com.staysync.usuarios.exception.EmailDuplicadoException;
import com.staysync.usuarios.model.Usuario;
import com.staysync.usuarios.repository.UsuarioRepository;
import com.staysync.usuarios.tenant.dto.CrearHotelRequest;
import com.staysync.usuarios.tenant.dto.HotelResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AltaHotelService - Tests Unitarios")
class AltaHotelServiceTest {

    @Mock private HotelService hotelService;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private AltaHotelService altaHotelService;

    private CrearHotelRequest request;

    @BeforeEach
    void setUp() {
        CrearHotelRequest.AdminInicial admin = new CrearHotelRequest.AdminInicial();
        admin.setNombre("Paula");
        admin.setApellido("Soto");
        admin.setEmail("Admin@HotelPacifico.cl");
        admin.setPassword("Admin@1234");
        request = new CrearHotelRequest();
        request.setNombre("Hotel Pacífico");
        request.setSlug("hotel-pacifico");
        request.setAdmin(admin);
    }

    @Test
    @DisplayName("Crea el hotel y su administrador inicial con rol ADMIN en ese hotel")
    void creaHotelYAdmin() {
        when(usuarioRepository.existsByEmail("admin@hotelpacifico.cl")).thenReturn(false);
        when(hotelService.crear("Hotel Pacífico", "hotel-pacifico", null, null))
                .thenReturn(HotelResponse.builder().tenantId("hotel-pacifico").slug("hotel-pacifico").build());
        when(passwordEncoder.encode("Admin@1234")).thenReturn("hash");

        HotelResponse hotel = altaHotelService.crear(request, "superadmin@staysync.com");

        assertThat(hotel.getTenantId()).isEqualTo("hotel-pacifico");
        ArgumentCaptor<Usuario> admin = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(admin.capture());
        assertThat(admin.getValue().getRol()).isEqualTo(Usuario.Rol.ADMIN);
        assertThat(admin.getValue().getTenantId()).isEqualTo("hotel-pacifico");
        assertThat(admin.getValue().getEmail()).isEqualTo("admin@hotelpacifico.cl");
        assertThat(admin.getValue().getPasswordHash()).isEqualTo("hash");
    }

    @Test
    @DisplayName("Si el email del administrador ya existe no crea el hotel")
    void emailDuplicadoNoCreaHotel() {
        when(usuarioRepository.existsByEmail("admin@hotelpacifico.cl")).thenReturn(true);

        assertThatThrownBy(() -> altaHotelService.crear(request, "superadmin@staysync.com"))
                .isInstanceOf(EmailDuplicadoException.class);
        verifyNoInteractions(hotelService);
        verify(usuarioRepository, never()).save(any());
    }
}
