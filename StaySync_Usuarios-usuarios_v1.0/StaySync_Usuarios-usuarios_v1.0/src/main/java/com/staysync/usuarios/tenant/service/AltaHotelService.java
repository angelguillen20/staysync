package com.staysync.usuarios.tenant.service;

import com.staysync.usuarios.exception.EmailDuplicadoException;
import com.staysync.usuarios.model.Usuario;
import com.staysync.usuarios.repository.UsuarioRepository;
import com.staysync.usuarios.tenant.dto.CrearHotelRequest;
import com.staysync.usuarios.tenant.dto.HotelResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de un hotel con su primer administrador, en una sola transacción: si el email del
 * administrador ya existe no queda un hotel creado sin nadie que lo gestione.
 * Los demás servicios no necesitan preparar nada: crean sus datos del hotel al usarlos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AltaHotelService {

    private final HotelService hotelService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public HotelResponse crear(CrearHotelRequest request, String emailSolicitante) {
        CrearHotelRequest.AdminInicial admin = request.getAdmin();
        String email = admin.getEmail().trim().toLowerCase();
        if (usuarioRepository.existsByEmail(email)) {
            throw new EmailDuplicadoException(email);
        }

        HotelResponse hotel = hotelService.crear(
                request.getNombre(), request.getSlug(), request.getLogoUrl(), request.getTheme());

        usuarioRepository.save(Usuario.builder()
                .tenantId(hotel.getTenantId())
                .nombre(admin.getNombre().trim())
                .apellido(admin.getApellido().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(admin.getPassword()))
                .rol(Usuario.Rol.ADMIN)
                .activo(true)
                .build());

        log.info("Hotel {} creado por {} con administrador {}", hotel.getTenantId(), emailSolicitante, email);
        return hotel;
    }
}
