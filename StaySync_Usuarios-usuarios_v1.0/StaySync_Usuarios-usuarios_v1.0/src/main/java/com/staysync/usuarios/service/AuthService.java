package com.staysync.usuarios.service;

import com.staysync.usuarios.dto.request.LoginRequest;
import com.staysync.usuarios.dto.request.RegistroRequest;
import com.staysync.usuarios.dto.response.AuthResponse;
import com.staysync.usuarios.dto.response.UsuarioResponse;
import com.staysync.usuarios.exception.TokenInvalidoException;
import com.staysync.usuarios.model.RefreshToken;
import com.staysync.usuarios.model.Usuario;
import com.staysync.usuarios.repository.RefreshTokenRepository;
import com.staysync.usuarios.repository.UsuarioRepository;
import com.staysync.usuarios.tenant.service.HotelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String CLAIM_USER_ID   = "userId";
    private static final String CLAIM_TENANT_ID = "tenantId";

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final HotelService hotelService;

    @Value("${jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow();

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());

        String accessToken  = jwtService.generateToken(userDetails, claimsDe(usuario));
        String refreshToken = crearRefreshToken(usuario);

        log.info("Login exitoso: {}", usuario.getEmail());
        return buildAuthResponse(accessToken, refreshToken, usuario);
    }

    @Transactional
    public UsuarioResponse registro(RegistroRequest request) {
        return usuarioService.registrar(request);
    }

    @Transactional
    public AuthResponse loginConGoogle(String email, String nombre, String apellido, String hotelSlug) {
        Usuario usuario = usuarioService.buscarOCrearPorGoogle(email, nombre, apellido, hotelSlug);

        UserDetails userDetails = userDetailsService.loadUserByUsername(usuario.getEmail());
        String accessToken  = jwtService.generateToken(userDetails, claimsDe(usuario));
        String refreshToken = crearRefreshToken(usuario);

        log.info("Login con Google exitoso: {}", usuario.getEmail());
        return buildAuthResponse(accessToken, refreshToken, usuario);
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenStr) {
        RefreshToken stored = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new TokenInvalidoException("Refresh token no encontrado"));

        if (stored.isExpirado()) {
            refreshTokenRepository.delete(stored);
            throw new TokenInvalidoException("Refresh token expirado");
        }

        Usuario usuario = stored.getUsuario();
        UserDetails userDetails = userDetailsService.loadUserByUsername(usuario.getEmail());
        String nuevoAccess = jwtService.generateToken(userDetails, claimsDe(usuario));

        // Rotar refresh token
        refreshTokenRepository.delete(stored);
        String nuevoRefresh = crearRefreshToken(usuario);

        return buildAuthResponse(nuevoAccess, nuevoRefresh, usuario);
    }

    @Transactional
    public void logout(String refreshTokenStr) {
        refreshTokenRepository.findByToken(refreshTokenStr)
                .ifPresent(refreshTokenRepository::delete);
        log.info("Logout: refresh token invalidado");
    }

    private String crearRefreshToken(Usuario usuario) {
        RefreshToken rt = RefreshToken.builder()
                .usuario(usuario)
                .token(UUID.randomUUID().toString())
                .expiraEn(LocalDateTime.now(ZoneId.systemDefault()).plusSeconds(refreshExpirationMs / 1000))
                .build();
        return refreshTokenRepository.save(rt).getToken();
    }

    /**
     * Claims propios de StaySync. tenantId es la fuente de verdad del hotel: el BFF lo
     * lee del token firmado y lo reenvía a los microservicios como X-Tenant-Id.
     */
    private Map<String, Object> claimsDe(Usuario usuario) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("rol", usuario.getRol().name());
        claims.put(CLAIM_USER_ID, usuario.getId());
        if (usuario.getTenantId() != null) {
            claims.put(CLAIM_TENANT_ID, usuario.getTenantId());
        }
        return claims;
    }

    private AuthResponse buildAuthResponse(String access, String refresh, Usuario usuario) {
        return AuthResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMs())
                .userId(usuario.getId())
                .nombreCompleto(usuario.getNombre() + " " + usuario.getApellido())
                .email(usuario.getEmail())
                .rol(usuario.getRol())
                .tenantId(usuario.getTenantId())
                .hotel(hotelService.buscarPorTenantId(usuario.getTenantId()).orElse(null))
                .build();
    }
}
