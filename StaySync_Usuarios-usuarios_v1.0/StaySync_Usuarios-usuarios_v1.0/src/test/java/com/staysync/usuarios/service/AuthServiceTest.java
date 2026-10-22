package com.staysync.usuarios.service;

import com.staysync.usuarios.dto.request.LoginRequest;
import com.staysync.usuarios.dto.response.AuthResponse;
import com.staysync.usuarios.exception.TokenInvalidoException;
import com.staysync.usuarios.model.RefreshToken;
import com.staysync.usuarios.model.Usuario;
import com.staysync.usuarios.repository.RefreshTokenRepository;
import com.staysync.usuarios.repository.UsuarioRepository;
import com.staysync.usuarios.tenant.dto.HotelResponse;
import com.staysync.usuarios.tenant.service.HotelService;
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
import java.util.Map;
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
    @Mock private HotelService hotelService;
    @Mock private UserDetails userDetails;

    @InjectMocks private AuthService authService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder()
                .id(1L)
                .tenantId("staysync-demo")
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
        HotelResponse hotel = HotelResponse.builder()
                .tenantId("staysync-demo").nombre("StaySync Demo").slug("staysync-demo")
                .theme(Map.of("primary", "#1F4E79"))
                .build();
        when(hotelService.buscarPorTenantId("staysync-demo")).thenReturn(Optional.of(hotel));

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-uuid");
        assertThat(response.getEmail()).isEqualTo("admin@test.com");
        assertThat(response.getRol()).isEqualTo(Usuario.Rol.ADMIN);
        assertThat(response.getTenantId()).isEqualTo("staysync-demo");
        assertThat(response.getHotel().getTheme()).containsEntry("primary", "#1F4E79");
    }

    @Test
    @DisplayName("login() - el JWT debe llevar el claim tenantId del usuario")
    @SuppressWarnings("unchecked")
    void debeIncluirTenantIdEnClaimsDelJwt() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@test.com");
        request.setPassword("Pass@123");

        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(usuario));
        when(userDetailsService.loadUserByUsername("admin@test.com")).thenReturn(userDetails);
        when(jwtService.generateToken(eq(userDetails), anyMap())).thenReturn("jwt-token");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(hotelService.buscarPorTenantId("staysync-demo")).thenReturn(Optional.empty());

        authService.login(request);

        ArgumentCaptor<Map<String, Object>> claims = ArgumentCaptor.forClass(Map.class);
        verify(jwtService).generateToken(eq(userDetails), claims.capture());
        assertThat(claims.getValue())
                .containsEntry("tenantId", "staysync-demo")
                .containsEntry("rol", "ADMIN")
                .containsEntry("userId", 1L);
    }

    @Test
    @DisplayName("login() - usuario sin hotel no debe llevar claim tenantId")
    @SuppressWarnings("unchecked")
    void noDebeIncluirTenantIdSiUsuarioNoTieneHotel() {
        usuario.setTenantId(null);
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@test.com");
        request.setPassword("Pass@123");

        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(usuario));
        when(userDetailsService.loadUserByUsername("admin@test.com")).thenReturn(userDetails);
        when(jwtService.generateToken(eq(userDetails), anyMap())).thenReturn("jwt-token");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(hotelService.buscarPorTenantId(null)).thenReturn(Optional.empty());

        AuthResponse response = authService.login(request);

        ArgumentCaptor<Map<String, Object>> claims = ArgumentCaptor.forClass(Map.class);
        verify(jwtService).generateToken(eq(userDetails), claims.capture());
        assertThat(claims.getValue()).doesNotContainKey("tenantId");
        assertThat(response.getHotel()).isNull();
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

    @Nested
    @DisplayName("Un huésped, una cuenta para todos los hoteles")
    class HuespedMultiHotel {

        private Usuario huesped;

        @BeforeEach
        void huespedDeDemo() {
            huesped = Usuario.builder().id(4L).tenantId("staysync-demo").nombre("Ana").apellido("Rojas")
                    .email("ana@correo.cl").passwordHash("$2a$12$hashed").rol(Usuario.Rol.HUESPED).activo(true).build();
        }

        private LoginRequest loginDesde(String email, String slug) {
            LoginRequest request = new LoginRequest();
            request.setEmail(email);
            request.setPassword("Pass@123");
            request.setHotelSlug(slug);
            return request;
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> claimsDelToken() {
            ArgumentCaptor<Map<String, Object>> claims = ArgumentCaptor.forClass(Map.class);
            verify(jwtService).generateToken(eq(userDetails), claims.capture());
            return claims.getValue();
        }

        @Test
        @DisplayName("Entra al hotel del portal y la sesión recuerda ese hotel para el refresh")
        void huespedEntraAlHotelDelPortal() {
            when(usuarioRepository.findByEmail("ana@correo.cl")).thenReturn(Optional.of(huesped));
            when(userDetailsService.loadUserByUsername("ana@correo.cl")).thenReturn(userDetails);
            when(hotelService.resolverTenantId("hotel-andes")).thenReturn("hotel-andes");
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AuthResponse response = authService.login(loginDesde("ana@correo.cl", "hotel-andes"));

            assertThat(claimsDelToken()).containsEntry("tenantId", "hotel-andes");
            assertThat(response.getTenantId()).isEqualTo("hotel-andes");
            ArgumentCaptor<RefreshToken> rt = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(rt.capture());
            assertThat(rt.getValue().getTenantId()).isEqualTo("hotel-andes");
        }

        @Test
        @DisplayName("El personal queda siempre en su hotel aunque entre por otro portal")
        void personalIgnoraElPortal() {
            when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(usuario));
            when(userDetailsService.loadUserByUsername("admin@test.com")).thenReturn(userDetails);
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            authService.login(loginDesde("admin@test.com", "hotel-andes"));

            assertThat(claimsDelToken()).containsEntry("tenantId", "staysync-demo");
            verify(hotelService, never()).resolverTenantId(any());
        }

        @Test
        @DisplayName("Un portal que no existe no impide entrar: usa su hotel")
        void portalInexistenteUsaSuHotel() {
            when(usuarioRepository.findByEmail("ana@correo.cl")).thenReturn(Optional.of(huesped));
            when(userDetailsService.loadUserByUsername("ana@correo.cl")).thenReturn(userDetails);
            when(hotelService.resolverTenantId("no-existe"))
                    .thenThrow(new com.staysync.usuarios.tenant.exception.HotelNotFoundException("no-existe"));
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            authService.login(loginDesde("ana@correo.cl", "no-existe"));

            assertThat(claimsDelToken()).containsEntry("tenantId", "staysync-demo");
        }

        @Test
        @DisplayName("refresh() con hotelSlug cambia al huésped de hotel (selector de portales)")
        void refreshCambiaDeHotel() {
            RefreshToken stored = RefreshToken.builder().token("rt").usuario(huesped).tenantId("staysync-demo")
                    .expiraEn(LocalDateTime.now().plusDays(1)).build();
            when(refreshTokenRepository.findByToken("rt")).thenReturn(Optional.of(stored));
            when(userDetailsService.loadUserByUsername("ana@correo.cl")).thenReturn(userDetails);
            when(hotelService.resolverTenantId("hotel-andes")).thenReturn("hotel-andes");
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AuthResponse response = authService.refresh("rt", "hotel-andes");

            assertThat(claimsDelToken()).containsEntry("tenantId", "hotel-andes");
            assertThat(response.getTenantId()).isEqualTo("hotel-andes");
            verify(refreshTokenRepository).delete(stored);
        }

        @Test
        @DisplayName("refresh() con hotelSlug no mueve al personal de su hotel")
        void refreshNoMueveAlPersonal() {
            RefreshToken stored = RefreshToken.builder().token("rt").usuario(usuario).tenantId("staysync-demo")
                    .expiraEn(LocalDateTime.now().plusDays(1)).build();
            when(refreshTokenRepository.findByToken("rt")).thenReturn(Optional.of(stored));
            when(userDetailsService.loadUserByUsername("admin@test.com")).thenReturn(userDetails);
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            authService.refresh("rt", "hotel-andes");

            assertThat(claimsDelToken()).containsEntry("tenantId", "staysync-demo");
        }

        @Test
        @DisplayName("refresh() mantiene al huésped en el hotel de su sesión")
        void refreshMantieneElHotel() {
            RefreshToken stored = RefreshToken.builder().token("rt").usuario(huesped).tenantId("hotel-andes")
                    .expiraEn(LocalDateTime.now().plusDays(1)).build();
            when(refreshTokenRepository.findByToken("rt")).thenReturn(Optional.of(stored));
            when(userDetailsService.loadUserByUsername("ana@correo.cl")).thenReturn(userDetails);
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AuthResponse response = authService.refresh("rt");

            assertThat(claimsDelToken()).containsEntry("tenantId", "hotel-andes");
            assertThat(response.getTenantId()).isEqualTo("hotel-andes");
        }
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
