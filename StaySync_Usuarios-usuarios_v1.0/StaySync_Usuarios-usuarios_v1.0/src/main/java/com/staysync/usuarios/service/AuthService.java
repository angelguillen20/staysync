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
import com.staysync.usuarios.tenant.exception.HotelNotFoundException;
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

        String tenantId     = hotelDeSesion(usuario, request.getHotelSlug());
        String accessToken  = jwtService.generateToken(userDetails, claimsDe(usuario, tenantId));
        String refreshToken = crearRefreshToken(usuario, tenantId);

        log.info("Login exitoso: {} (hotel {})", usuario.getEmail(), tenantId);
        return buildAuthResponse(accessToken, refreshToken, usuario, tenantId);
    }

    @Transactional
    public UsuarioResponse registro(RegistroRequest request) {
        return usuarioService.registrar(request);
    }

    @Transactional
    public AuthResponse loginConGoogle(String email, String nombre, String apellido, String hotelSlug) {
        Usuario usuario = usuarioService.buscarOCrearPorGoogle(email, nombre, apellido, hotelSlug);

        UserDetails userDetails = userDetailsService.loadUserByUsername(usuario.getEmail());
        String tenantId     = hotelDeSesion(usuario, hotelSlug);
        String accessToken  = jwtService.generateToken(userDetails, claimsDe(usuario, tenantId));
        String refreshToken = crearRefreshToken(usuario, tenantId);

        log.info("Login con Google exitoso: {} (hotel {})", usuario.getEmail(), tenantId);
        return buildAuthResponse(accessToken, refreshToken, usuario, tenantId);
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenStr) {
        return refresh(refreshTokenStr, null);
    }

    /**
     * Renueva la sesión. Con hotelSlug, un huésped pasa al hotel de ese portal sin volver a
     * escribir su contraseña (selector de portales); el personal sigue en su hotel.
     */
    @Transactional
    public AuthResponse refresh(String refreshTokenStr, String hotelSlug) {
        RefreshToken stored = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new TokenInvalidoException("Refresh token no encontrado"));

        if (stored.isExpirado()) {
            refreshTokenRepository.delete(stored);
            throw new TokenInvalidoException("Refresh token expirado");
        }

        Usuario usuario = stored.getUsuario();
        // Un huésped sigue en el hotel del portal donde inició sesión; el personal, en el suyo
        String tenantId;
        if (hotelSlug != null && !hotelSlug.isBlank()) {
            tenantId = hotelDeSesion(usuario, hotelSlug);
        } else {
            tenantId = usuario.getRol() == Usuario.Rol.HUESPED && stored.getTenantId() != null
                    ? stored.getTenantId()
                    : usuario.getTenantId();
        }
        UserDetails userDetails = userDetailsService.loadUserByUsername(usuario.getEmail());
        String nuevoAccess = jwtService.generateToken(userDetails, claimsDe(usuario, tenantId));

        // Rotar refresh token
        refreshTokenRepository.delete(stored);
        String nuevoRefresh = crearRefreshToken(usuario, tenantId);

        return buildAuthResponse(nuevoAccess, nuevoRefresh, usuario, tenantId);
    }

    @Transactional
    public void logout(String refreshTokenStr) {
        refreshTokenRepository.findByToken(refreshTokenStr)
                .ifPresent(refreshTokenRepository::delete);
        log.info("Logout: refresh token invalidado");
    }

    /**
     * Hotel de la sesión. Un huésped tiene una sola cuenta para toda la cadena: entra al hotel
     * del portal que usa (sus reservas de cada hotel quedan guardadas en ese hotel). El personal
     * siempre trabaja en el hotel asignado. Un slug inválido no impide entrar: se usa el suyo.
     */
    private String hotelDeSesion(Usuario usuario, String hotelSlug) {
        if (usuario.getRol() != Usuario.Rol.HUESPED || hotelSlug == null || hotelSlug.isBlank()) {
            return usuario.getTenantId();
        }
        try {
            return hotelService.resolverTenantId(hotelSlug);
        } catch (HotelNotFoundException e) {
            log.warn("Portal '{}' no encontrado al iniciar sesión {}: se usa su hotel", hotelSlug, usuario.getEmail());
            return usuario.getTenantId();
        }
    }

    private String crearRefreshToken(Usuario usuario, String tenantId) {
        RefreshToken rt = RefreshToken.builder()
                .usuario(usuario)
                .tenantId(tenantId)
                .token(UUID.randomUUID().toString())
                .expiraEn(LocalDateTime.now(ZoneId.systemDefault()).plusSeconds(refreshExpirationMs / 1000))
                .build();
        return refreshTokenRepository.save(rt).getToken();
    }

    /**
     * Claims propios de StaySync. tenantId es la fuente de verdad del hotel: el BFF lo
     * lee del token firmado y lo reenvía a los microservicios como X-Tenant-Id.
     */
    private Map<String, Object> claimsDe(Usuario usuario, String tenantId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("rol", usuario.getRol().name());
        claims.put(CLAIM_USER_ID, usuario.getId());
        if (tenantId != null) {
            claims.put(CLAIM_TENANT_ID, tenantId);
        }
        return claims;
    }

    private AuthResponse buildAuthResponse(String access, String refresh, Usuario usuario, String tenantId) {
        return AuthResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMs())
                .userId(usuario.getId())
                .nombreCompleto(usuario.getNombre() + " " + usuario.getApellido())
                .email(usuario.getEmail())
                .rol(usuario.getRol())
                .tenantId(tenantId)
                .hotel(hotelService.buscarPorTenantId(tenantId).orElse(null))
                .build();
    }
}
