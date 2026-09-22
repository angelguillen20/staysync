package com.staysync.usuarios.service;

import com.staysync.usuarios.dto.request.ActualizarUsuarioRequest;
import com.staysync.usuarios.dto.request.RegistroRequest;
import com.staysync.usuarios.dto.response.UsuarioResponse;
import com.staysync.usuarios.exception.EmailDuplicadoException;
import com.staysync.usuarios.exception.UsuarioNotFoundException;
import com.staysync.usuarios.mapper.UsuarioMapper;
import com.staysync.usuarios.model.Usuario;
import com.staysync.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
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

    @InjectMocks private UsuarioService usuarioService;

    private Usuario usuarioBase;
    private UsuarioResponse responseBase;

    @BeforeEach
    void setUp() {
        usuarioBase = Usuario.builder()
                .id(1L)
                .nombre("Juan")
                .apellido("García")
                .email("juan@test.com")
                .passwordHash("$2a$12$hashed")
                .rol(Usuario.Rol.HUESPED)
                .activo(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        responseBase = UsuarioResponse.builder()
                .id(1L)
                .nombre("Juan")
                .apellido("García")
                .email("juan@test.com")
                .rol(Usuario.Rol.HUESPED)
                .activo(true)
                .build();
    }

    @Nested
    @DisplayName("registrar()")
    class RegistrarTests {

        @Test
        @DisplayName("Debe registrar usuario correctamente")
        void debeRegistrarUsuario() {
            RegistroRequest request = new RegistroRequest();
            request.setNombre("Juan");
            request.setApellido("García");
            request.setEmail("juan@test.com");
            request.setPassword("MiPass@123");
            request.setRol(Usuario.Rol.HUESPED);

            when(usuarioRepository.existsByEmail(anyString())).thenReturn(false);
            when(usuarioMapper.toEntity(request)).thenReturn(usuarioBase);
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashed");
            when(usuarioRepository.save(any())).thenReturn(usuarioBase);
            when(usuarioMapper.toResponse(usuarioBase)).thenReturn(responseBase);

            UsuarioResponse result = usuarioService.registrar(request);

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("juan@test.com");
            verify(usuarioRepository).save(any(Usuario.class));
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
        @DisplayName("Debe retornar usuario cuando existe y está activo")
        void debeRetornarUsuarioActivo() {
            when(usuarioRepository.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(usuarioBase));
            when(usuarioMapper.toResponse(usuarioBase)).thenReturn(responseBase);

            UsuarioResponse result = usuarioService.obtenerPorId(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Debe lanzar UsuarioNotFoundException si no existe")
        void debeLanzarExcepcionNoEncontrado() {
            when(usuarioRepository.findByIdAndActivoTrue(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> usuarioService.obtenerPorId(99L))
                    .isInstanceOf(UsuarioNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("listarTodos()")
    class ListarTests {

        @Test
        @DisplayName("Debe retornar página de usuarios")
        void debeRetornarPaginaUsuarios() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Usuario> page = new PageImpl<>(List.of(usuarioBase));
            when(usuarioRepository.findAll(pageable)).thenReturn(page);
            when(usuarioMapper.toResponse(usuarioBase)).thenReturn(responseBase);

            Page<UsuarioResponse> result = usuarioService.listarTodos(pageable);

            assertThat(result).isNotEmpty();
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("desactivar()")
    class DesactivarTests {

        @Test
        @DisplayName("Debe desactivar usuario existente")
        void debeDesactivarUsuario() {
            when(usuarioRepository.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(usuarioBase));
            when(usuarioRepository.save(any())).thenReturn(usuarioBase);

            usuarioService.desactivar(1L);

            assertThat(usuarioBase.getActivo()).isFalse();
            verify(usuarioRepository).save(usuarioBase);
        }

        @Test
        @DisplayName("Debe lanzar excepción si usuario no existe")
        void debeLanzarExcepcionAlDesactivarInexistente() {
            when(usuarioRepository.findByIdAndActivoTrue(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> usuarioService.desactivar(99L))
                    .isInstanceOf(UsuarioNotFoundException.class);
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

            when(usuarioRepository.findByIdAndActivoTrue(1L)).thenReturn(Optional.of(usuarioBase));
            when(usuarioRepository.save(any())).thenReturn(usuarioBase);
            when(usuarioMapper.toResponse(any())).thenReturn(responseBase);

            UsuarioResponse result = usuarioService.actualizar(1L, request);

            assertThat(result).isNotNull();
            verify(usuarioMapper).updateEntityFromRequest(eq(usuarioBase), eq(request));
        }
    }
}
