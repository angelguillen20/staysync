package com.staysync.usuarios.service;

import com.staysync.usuarios.dto.request.LoginRequest;
import com.staysync.usuarios.dto.response.AuthResponse;
import com.staysync.usuarios.exception.TokenInvalidoException;
import com.staysync.usuarios.model.RefreshToken;
import com.staysync.usuarios.model.Usuario;
import com.staysync.usuarios.repository.RefreshTokenRepository;
import com.staysync.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService - Tests Unitarios")
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserDetailsService userDetailsService;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private UsuarioService usuarioService;
    @Mock private UserDetails userDetails;

    @InjectMocks private AuthService authService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder()
                .id(1L)
                .nombre("Admin")
                .apellido("StaySync")
                .email("admin@test.com")
                .passwordHash("$2a$12$hashed")
                .rol(Usuario.Rol.ADMIN)
                .activo(true)
                .build();
    }

    @Test
    @DisplayName("login() - debe retornar AuthResponse con tokens cuando credenciales son correctas")
    void debeRetornarAuthResponseEnLoginExitoso() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@test.com");
        request.setPassword("Pass@123");

        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(usuario));
        when(userDetailsService.loadUserByUsername("admin@test.com")).thenReturn(userDetails);
        when(jwtService.generateToken(eq(userDetails), anyMap())).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);
        RefreshToken rt = RefreshToken.builder()
                .token("refresh-uuid")
                .usuario(usuario)
                .expiraEn(LocalDateTime.now().plusDays(7))
                .build();
        when(refreshTokenRepository.save(any())).thenReturn(rt);

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-uuid");
        assertThat(response.getEmail()).isEqualTo("admin@test.com");
        assertThat(response.getRol()).isEqualTo(Usuario.Rol.ADMIN);
    }

    @Test
    @DisplayName("login() - debe propagar BadCredentialsException con credenciales incorrectas")
    void debeLanzarExcepcionConCredencialesIncorrectas() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@test.com");
        request.setPassword("wrong");

        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("refresh() - debe lanzar TokenInvalidoException con refresh token inexistente")
    void debeLanzarExcepcionConRefreshTokenInexistente() {
        when(refreshTokenRepository.findByToken("token-invalido")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("token-invalido"))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("no encontrado");
    }

    @Test
    @DisplayName("refresh() - debe lanzar TokenInvalidoException con refresh token expirado")
    void debeLanzarExcepcionConRefreshTokenExpirado() {
        RefreshToken expired = RefreshToken.builder()
                .token("expired-token")
                .usuario(usuario)
                .expiraEn(LocalDateTime.now().minusDays(1))
                .build();

        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("expired-token"))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("expirado");

        verify(refreshTokenRepository).delete(expired);
    }

    @Test
    @DisplayName("logout() - debe eliminar refresh token si existe")
    void debeEliminarRefreshTokenEnLogout() {
        RefreshToken rt = RefreshToken.builder()
                .token("valid-token")
                .usuario(usuario)
                .expiraEn(LocalDateTime.now().plusDays(7))
                .build();
        when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(rt));

        authService.logout("valid-token");

        verify(refreshTokenRepository).delete(rt);
    }

    @Test
    @DisplayName("logout() - no debe lanzar excepción si el token no existe")
    void noDebeLanzarExcepcionEnLogoutConTokenInexistente() {
        when(refreshTokenRepository.findByToken("no-existe")).thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(() -> authService.logout("no-existe"));
    }
}
