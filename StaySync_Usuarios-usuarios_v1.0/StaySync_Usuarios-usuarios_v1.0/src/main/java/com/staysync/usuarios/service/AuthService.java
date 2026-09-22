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
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;

    @Value("${jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow();

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());

        Map<String, Object> claims = Map.of(
                "rol", usuario.getRol().name(),
                "userId", usuario.getId()
        );
        String accessToken  = jwtService.generateToken(userDetails, claims);
        String refreshToken = crearRefreshToken(usuario);

        log.info("Login exitoso: {}", usuario.getEmail());
        return buildAuthResponse(accessToken, refreshToken, usuario);
    }

    @Transactional
    public UsuarioResponse registro(RegistroRequest request) {
        return usuarioService.registrar(request);
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
        String nuevoAccess = jwtService.generateToken(userDetails,
                Map.of("rol", usuario.getRol().name(), "userId", usuario.getId()));

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
                .expiraEn(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000))
                .build();
        return refreshTokenRepository.save(rt).getToken();
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
                .build();
    }
}
