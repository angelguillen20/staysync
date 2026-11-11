package com.staysync.usuarios.service;

import com.staysync.usuarios.dto.request.ActualizarUsuarioRequest;
import com.staysync.usuarios.dto.request.RegistroRequest;
import com.staysync.usuarios.dto.response.UsuarioResponse;
import com.staysync.usuarios.exception.EmailDuplicadoException;
import com.staysync.usuarios.exception.OperacionNoPermitidaException;
import com.staysync.usuarios.exception.UsuarioNotFoundException;
import com.staysync.usuarios.mapper.UsuarioMapper;
import com.staysync.usuarios.model.Usuario;
import com.staysync.usuarios.repository.RefreshTokenRepository;
import com.staysync.usuarios.repository.UsuarioRepository;
import com.staysync.usuarios.tenant.service.HotelService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsuarioService - Tests Unitarios")
class UsuarioServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UsuarioMapper usuarioMapper;
    @Mock private HotelService hotelService;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks private UsuarioService usuarioService;

    private Usuario usuarioBase;
    private UsuarioResponse responseBase;
    private Usuario adminDemo;
    private Usuario adminAndes;
    private Usuario superAdmin;

    private static Usuario cuenta(long id, String email, Usuario.Rol rol, String tenantId) {
        return Usuario.builder().id(id).email(email).nombre("N").apellido("A")
                .passwordHash("$2a$12$hashed").rol(rol).tenantId(tenantId).activo(true).build();
    }

    @BeforeEach
    void setUp() {
        usuarioBase = Usuario.builder()
                .id(1L)
                .tenantId("staysync-demo")
                .nombre("Juan")
                .apellido("García")
                .email("juan@test.com")
                .passwordHash("$2a$12$hashed")
                .rol(Usuario.Rol.HUESPED)
                .activo(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        adminDemo  = cuenta(10L, "admin@test.com", Usuario.Rol.ADMIN,       "staysync-demo");
        adminAndes = cuenta(20L, "andes@test.com", Usuario.Rol.ADMIN,       "hotel-andes");
        superAdmin = cuenta(30L, "super@test.com", Usuario.Rol.SUPER_ADMIN, "staysync-demo");

        responseBase = UsuarioResponse.builder()
                .id(1L)
                .nombre("Juan")
                .apellido("García")
                .email("juan@test.com")
                .rol(Usuario.Rol.HUESPED)
                .activo(true)
                .build();
    }

    private void comoSolicitante(Usuario solicitante) {
        when(usuarioRepository.findByEmail(solicitante.getEmail())).thenReturn(Optional.of(solicitante));
    }

    @Nested
    @DisplayName("registrar()")
    class RegistrarTests {

        @Test
        @DisplayName("Debe registrar huésped en el hotel indicado")
        void debeRegistrarUsuario() {
            RegistroRequest request = new RegistroRequest();
            request.setNombre("Juan");
            request.setApellido("García");
            request.setEmail("juan@test.com");
            request.setPassword("MiPass@123");
            request.setHotelSlug("hotel-andes");
            usuarioBase.setRol(Usuario.Rol.ADMIN); // aunque el mapper devolviera otro rol

            when(usuarioRepository.existsByEmail(anyString())).thenReturn(false);
            when(usuarioMapper.toEntity(request)).thenReturn(usuarioBase);
            when(hotelService.resolverTenantId("hotel-andes")).thenReturn("hotel-andes");
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashed");
            when(usuarioRepository.save(any())).thenReturn(usuarioBase);
            when(usuarioMapper.toResponse(usuarioBase)).thenReturn(responseBase);

            UsuarioResponse result = usuarioService.registrar(request);

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("juan@test.com");
            verify(usuarioRepository).save(argThat(u ->
                    "hotel-andes".equals(u.getTenantId()) && u.getRol() == Usuario.Rol.HUESPED));
        }

        @Test
        @DisplayName("Debe lanzar EmailDuplicadoException si el email ya existe")
        void debeLanzarExcepcionEmailDuplicado() {
            RegistroRequest request = new RegistroRequest();
            request.setEmail("existente@test.com");

            when(usuarioRepository.existsByEmail("existente@test.com")).thenReturn(true);

            assertThatThrownBy(() -> usuarioService.registrar(request))
                    .isInstanceOf(EmailDuplicadoException.class)
                    .hasMessageContaining("existente@test.com");

            verify(usuarioRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("obtenerPorId()")
    class ObtenerPorIdTests {

        @Test
        @DisplayName("Debe retornar usuario de su mismo hotel")
        void debeRetornarUsuarioActivo() {
            comoSolicitante(adminDemo);
            when(usuarioRepository.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(usuarioBase));
            when(usuarioMapper.toResponse(usuarioBase)).thenReturn(responseBase);

            UsuarioResponse result = usuarioService.obtenerPorId(1L, adminDemo.getEmail());

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Debe lanzar UsuarioNotFoundException si no existe")
        void debeLanzarExcepcionNoEncontrado() {
            comoSolicitante(adminDemo);
            when(usuarioRepository.findByIdAndActivoTrue(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> usuarioService.obtenerPorId(99L, adminDemo.getEmail()))
                    .isInstanceOf(UsuarioNotFoundException.class);
        }

        @Test
        @DisplayName("Un usuario de otro hotel responde como inexistente (404)")
        void usuarioDeOtroHotelEsInvisible() {
            comoSolicitante(adminAndes);
            when(usuarioRepository.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(usuarioBase));

            assertThatThrownBy(() -> usuarioService.obtenerPorId(1L, adminAndes.getEmail()))
                    .isInstanceOf(UsuarioNotFoundException.class);
        }

        @Test
        @DisplayName("SUPER_ADMIN ve usuarios de cualquier hotel")
        void superAdminVeTodo() {
            comoSolicitante(superAdmin);
            usuarioBase.setTenantId("hotel-andes");
            when(usuarioRepository.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(usuarioBase));
            when(usuarioMapper.toResponse(usuarioBase)).thenReturn(responseBase);

            assertThat(usuarioService.obtenerPorId(1L, superAdmin.getEmail())).isNotNull();
        }
    }

    @Nested
    @DisplayName("listarTodos()")
    class ListarTests {

        @Test
        @DisplayName("ADMIN solo lista los usuarios de su hotel, sin las cuentas SUPER_ADMIN")
        void adminListaSoloSuHotel() {
            comoSolicitante(adminDemo);
            Pageable pageable = PageRequest.of(0, 10);
            when(usuarioRepository.findByTenantIdAndRolNot("staysync-demo", Usuario.Rol.SUPER_ADMIN, pageable))
                    .thenReturn(new PageImpl<>(List.of(usuarioBase)));
            when(usuarioMapper.toResponse(usuarioBase)).thenReturn(responseBase);

            Page<UsuarioResponse> result = usuarioService.listarTodos(pageable, adminDemo.getEmail());

            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(usuarioRepository, never()).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("SUPER_ADMIN lista los usuarios de todos los hoteles")
        void superAdminListaTodo() {
            comoSolicitante(superAdmin);
            Pageable pageable = PageRequest.of(0, 10);
            when(usuarioRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(usuarioBase)));
            when(usuarioMapper.toResponse(usuarioBase)).thenReturn(responseBase);

            assertThat(usuarioService.listarTodos(pageable, superAdmin.getEmail())).hasSize(1);
            verify(usuarioRepository, never()).findByTenantId(anyString(), any());
        }

        @Test
        @DisplayName("SUPER_ADMIN puede filtrar por hotel")
        void superAdminFiltraPorHotel() {
            comoSolicitante(superAdmin);
            Pageable pageable = PageRequest.of(0, 10);
            when(usuarioRepository.findByTenantId("hotel-andes", pageable)).thenReturn(new PageImpl<>(List.of(usuarioBase)));
            when(usuarioMapper.toResponse(usuarioBase)).thenReturn(responseBase);

            assertThat(usuarioService.listarTodos(pageable, superAdmin.getEmail(), "hotel-andes")).hasSize(1);
            verify(usuarioRepository, never()).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("Un ADMIN no puede usar el filtro de hotel para ver otro hotel")
        void adminIgnoraFiltroDeHotel() {
            comoSolicitante(adminDemo);
            Pageable pageable = PageRequest.of(0, 10);
            when(usuarioRepository.findByTenantIdAndRolNot("staysync-demo", Usuario.Rol.SUPER_ADMIN, pageable))
                    .thenReturn(new PageImpl<>(List.of()));

            usuarioService.listarTodos(pageable, adminDemo.getEmail(), "hotel-andes");

            verify(usuarioRepository, never()).findByTenantId(anyString(), any());
        }
    }

    @Test
    @DisplayName("buscar() de un ADMIN consulta con filtro (por hotel)")
    void buscarConsultaConFiltro() {
        comoSolicitante(adminAndes);
        when(usuarioRepository.findAll(ArgumentMatchers.<Specification<Usuario>>any())).thenReturn(List.of());

        assertThat(usuarioService.buscar("ana", "HUESPED", adminAndes.getEmail())).isEmpty();
        verify(usuarioRepository).findAll(ArgumentMatchers.<Specification<Usuario>>any());
    }

    @Nested
    @DisplayName("desactivar()")
    class DesactivarTests {

        @Test
        @DisplayName("Debe desactivar usuario de su hotel y cerrar sus sesiones")
        void debeDesactivarUsuario() {
            comoSolicitante(adminDemo);
            when(usuarioRepository.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(usuarioBase));
            when(usuarioRepository.save(any())).thenReturn(usuarioBase);

            usuarioService.desactivar(1L, adminDemo.getEmail());

            assertThat(usuarioBase.getActivo()).isFalse();
            verify(usuarioRepository).save(usuarioBase);
            verify(refreshTokenRepository).deleteByUsuario(usuarioBase);
        }

        @Test
        @DisplayName("Debe lanzar excepción si usuario no existe")
        void debeLanzarExcepcionAlDesactivarInexistente() {
            comoSolicitante(adminDemo);
            when(usuarioRepository.findByIdAndActivoTrue(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> usuarioService.desactivar(99L, adminDemo.getEmail()))
                    .isInstanceOf(UsuarioNotFoundException.class);
        }

        @Test
        @DisplayName("ADMIN no puede desactivar a un SUPER_ADMIN")
        void adminNoDesactivaSuperAdmin() {
            comoSolicitante(adminDemo);
            when(usuarioRepository.findByIdAndActivoTrue(30L)).thenReturn(Optional.of(superAdmin));

            assertThatThrownBy(() -> usuarioService.desactivar(30L, adminDemo.getEmail()))
                    .isInstanceOf(OperacionNoPermitidaException.class);
            assertThat(superAdmin.getActivo()).isTrue();
        }
    }

    @Nested
    @DisplayName("actualizar()")
    class ActualizarTests {

        @Test
        @DisplayName("Debe actualizar datos del usuario")
        void debeActualizarUsuario() {
            ActualizarUsuarioRequest request = new ActualizarUsuarioRequest();
            request.setNombre("Carlos");

            comoSolicitante(adminDemo);
            when(usuarioRepository.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(usuarioBase));
            when(usuarioRepository.save(any())).thenReturn(usuarioBase);
            when(usuarioMapper.toResponse(any())).thenReturn(responseBase);

            UsuarioResponse result = usuarioService.actualizar(1L, request, adminDemo.getEmail());

            assertThat(result).isNotNull();
            verify(usuarioMapper).updateEntityFromRequest(eq(usuarioBase), eq(request));
        }

        @Test
        @DisplayName("ADMIN no puede cambiar la contraseña de un SUPER_ADMIN")
        void adminNoEditaSuperAdmin() {
            ActualizarUsuarioRequest request = new ActualizarUsuarioRequest();
            request.setNuevaPassword("Otra@1234");

            comoSolicitante(adminDemo);
            when(usuarioRepository.findByIdAndActivoTrue(30L)).thenReturn(Optional.of(superAdmin));

            assertThatThrownBy(() -> usuarioService.actualizar(30L, request, adminDemo.getEmail()))
                    .isInstanceOf(OperacionNoPermitidaException.class);
            verify(usuarioRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("cambiarRol()")
    class CambiarRolTests {

        @Test
        @DisplayName("Debe cambiar el rol de otro usuario y cerrar sus sesiones")
        void debeCambiarRol() {
            comoSolicitante(adminDemo);
            when(usuarioRepository.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(usuarioBase));
            when(usuarioRepository.save(any())).thenReturn(usuarioBase);
            when(usuarioMapper.toResponse(any())).thenReturn(responseBase);

            usuarioService.cambiarRol(1L, "recepcionista", adminDemo.getEmail());

            assertThat(usuarioBase.getRol()).isEqualTo(Usuario.Rol.RECEPCIONISTA);
            verify(usuarioRepository).save(usuarioBase);
            verify(refreshTokenRepository).deleteByUsuario(usuarioBase);
        }

        @Test
        @DisplayName("No debe permitir cambiar el rol propio")
        void noDebeCambiarRolPropio() {
            comoSolicitante(adminDemo);
            when(usuarioRepository.findByIdAndActivoTrue(10L)).thenReturn(Optional.of(adminDemo));

            assertThatThrownBy(() -> usuarioService.cambiarRol(10L, "HUESPED", adminDemo.getEmail()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("propio rol");
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("Debe rechazar un rol inexistente")
        void debeRechazarRolInvalido() {
            assertThatThrownBy(() -> usuarioService.cambiarRol(1L, "GERENTE", "admin@test.com"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Rol inválido");
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("ADMIN no puede otorgar el rol SUPER_ADMIN")
        void adminNoOtorgaSuperAdmin() {
            comoSolicitante(adminDemo);
            when(usuarioRepository.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(usuarioBase));

            assertThatThrownBy(() -> usuarioService.cambiarRol(1L, "SUPER_ADMIN", adminDemo.getEmail()))
                    .isInstanceOf(OperacionNoPermitidaException.class);
            assertThat(usuarioBase.getRol()).isEqualTo(Usuario.Rol.HUESPED);
        }

        @Test
        @DisplayName("ADMIN no puede cambiar el rol de un usuario de otro hotel")
        void adminNoCambiaRolEnOtroHotel() {
            comoSolicitante(adminAndes);
            when(usuarioRepository.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(usuarioBase));

            assertThatThrownBy(() -> usuarioService.cambiarRol(1L, "ADMIN", adminAndes.getEmail()))
                    .isInstanceOf(UsuarioNotFoundException.class);
            assertThat(usuarioBase.getRol()).isEqualTo(Usuario.Rol.HUESPED);
        }

        @Test
        @DisplayName("SUPER_ADMIN puede otorgar el rol SUPER_ADMIN")
        void superAdminOtorgaSuperAdmin() {
            comoSolicitante(superAdmin);
            when(usuarioRepository.findByIdAndActivoTrue(10L)).thenReturn(Optional.of(adminDemo));
            when(usuarioRepository.save(any())).thenReturn(adminDemo);
            when(usuarioMapper.toResponse(any())).thenReturn(responseBase);

            usuarioService.cambiarRol(10L, "SUPER_ADMIN", superAdmin.getEmail());

            assertThat(adminDemo.getRol()).isEqualTo(Usuario.Rol.SUPER_ADMIN);
        }
    }

    @Nested
    @DisplayName("cambiarHotel()")
    class CambiarHotelTests {

        @Test
        @DisplayName("SUPER_ADMIN mueve a un admin de hotel y cierra sus sesiones")
        void superAdminMueveAdmin() {
            comoSolicitante(superAdmin);
            when(usuarioRepository.findByIdAndActivoTrue(10L)).thenReturn(Optional.of(adminDemo));
            when(hotelService.verificarActivo("hotel-andes")).thenReturn("hotel-andes");
            when(usuarioRepository.save(any())).thenReturn(adminDemo);
            when(usuarioMapper.toResponse(any())).thenReturn(responseBase);

            usuarioService.cambiarHotel(10L, "hotel-andes", superAdmin.getEmail());

            assertThat(adminDemo.getTenantId()).isEqualTo("hotel-andes");
            verify(refreshTokenRepository).deleteByUsuario(adminDemo);
        }

        @Test
        @DisplayName("Un ADMIN de hotel no puede cambiar hoteles")
        void adminNoPuedeCambiarHotel() {
            comoSolicitante(adminDemo);

            assertThatThrownBy(() -> usuarioService.cambiarHotel(1L, "hotel-andes", adminDemo.getEmail()))
                    .isInstanceOf(OperacionNoPermitidaException.class);
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("Los huéspedes no se mueven de hotel")
        void huespedNoSeMueve() {
            comoSolicitante(superAdmin);
            when(usuarioRepository.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(usuarioBase));

            assertThatThrownBy(() -> usuarioService.cambiarHotel(1L, "hotel-andes", superAdmin.getEmail()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("personal");
            assertThat(usuarioBase.getTenantId()).isEqualTo("staysync-demo");
        }

        @Test
        @DisplayName("Mismo hotel no guarda ni cierra sesiones")
        void mismoHotelNoHaceNada() {
            comoSolicitante(superAdmin);
            when(usuarioRepository.findByIdAndActivoTrue(10L)).thenReturn(Optional.of(adminDemo));
            when(hotelService.verificarActivo("staysync-demo")).thenReturn("staysync-demo");
            when(usuarioMapper.toResponse(adminDemo)).thenReturn(responseBase);

            usuarioService.cambiarHotel(10L, "staysync-demo", superAdmin.getEmail());

            verify(usuarioRepository, never()).save(any());
            verifyNoInteractions(refreshTokenRepository);
        }
    }
}
