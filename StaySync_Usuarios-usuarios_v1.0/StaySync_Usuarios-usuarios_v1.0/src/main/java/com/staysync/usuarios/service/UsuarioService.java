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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gestión de usuarios con alcance por hotel: un ADMIN o RECEPCIONISTA solo ve y modifica
 * usuarios de su propio hotel; un SUPER_ADMIN ve todos. Fuera de su hotel, un usuario
 * "no existe" (404) para no revelar cuentas de otros hoteles.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper usuarioMapper;
    private final HotelService hotelService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public UsuarioResponse registrar(RegistroRequest request) {
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new EmailDuplicadoException(request.getEmail());
        }
        Usuario usuario = usuarioMapper.toEntity(request);
        usuario.setRol(Usuario.Rol.HUESPED);
        usuario.setTenantId(hotelService.resolverTenantId(request.getHotelSlug()));
        usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        Usuario guardado = usuarioRepository.save(usuario);
        log.info("Usuario registrado: {}", guardado.getEmail());
        return usuarioMapper.toResponse(guardado);
    }

    public Page<UsuarioResponse> listarTodos(Pageable pageable, String emailSolicitante) {
        return listarTodos(pageable, emailSolicitante, null);
    }

    /**
     * SUPER_ADMIN: todos los hoteles, o solo {@code tenantFiltro} si viene.
     * ADMIN: su hotel, sin las cuentas SUPER_ADMIN (son de la plataforma, no del hotel).
     */
    public Page<UsuarioResponse> listarTodos(Pageable pageable, String emailSolicitante, String tenantFiltro) {
        Pageable safePageable = (pageable != null) ? pageable : Pageable.unpaged();
        Usuario solicitante = solicitante(emailSolicitante);
        Page<Usuario> pagina;
        if (!esSuperAdmin(solicitante)) {
            pagina = usuarioRepository.findByTenantIdAndRolNot(
                    solicitante.getTenantId(), Usuario.Rol.SUPER_ADMIN, safePageable);
        } else if (tenantFiltro != null && !tenantFiltro.isBlank()) {
            pagina = usuarioRepository.findByTenantId(tenantFiltro, safePageable);
        } else {
            pagina = usuarioRepository.findAll(safePageable);
        }
        return pagina.map(u -> mapearSeguro(u, "listarTodos"));
    }

    public List<UsuarioResponse> listarHuespedes(String emailSolicitante) {
        log.debug("listarHuespedes: buscando todos los huéspedes activos");
        return buscar("", "HUESPED", emailSolicitante);
    }

    public List<UsuarioResponse> buscar(String q, String rol, String emailSolicitante) {
        return buscar(q, rol, emailSolicitante, null);
    }

    public List<UsuarioResponse> buscar(String q, String rol, String emailSolicitante, String tenantFiltro) {
        log.debug("buscar: q='{}', rol='{}', hotel='{}'", q, rol, tenantFiltro);
        Usuario solicitante = solicitante(emailSolicitante);

        Specification<Usuario> spec = (root, query, cb) -> cb.isTrue(root.get("activo"));

        if (!esSuperAdmin(solicitante)) {
            String tenantId = solicitante.getTenantId();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("tenantId"), tenantId))
                    .and((root, query, cb) -> cb.notEqual(root.get("rol"), Usuario.Rol.SUPER_ADMIN));
        } else if (tenantFiltro != null && !tenantFiltro.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("tenantId"), tenantFiltro));
        }

        if (rol != null && !rol.isBlank()) {
            Usuario.Rol rolEnum = parsearRol(rol);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("rol"), rolEnum));
        }

        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nombre")),   pattern),
                    cb.like(cb.lower(root.get("apellido")), pattern),
                    cb.like(cb.lower(root.get("email")),    pattern)
            ));
        }

        List<Usuario> resultados = usuarioRepository.findAll(spec);
        log.debug("buscar: {} resultado(s) encontrado(s)", resultados.size());

        return resultados.stream()
                .map(u -> mapearSeguro(u, "buscar"))
                .collect(Collectors.toList());
    }

    private UsuarioResponse mapearSeguro(Usuario u, String origen) {
        try {
            UsuarioResponse response = usuarioMapper.toResponse(u);
            if (response == null) {
                log.error("{}: mapper.toResponse retornó null para usuario id={}", origen, u.getId());
                return UsuarioResponse.builder()
                        .id(u.getId())
                        .tenantId(u.getTenantId())
                        .nombre(u.getNombre() != null ? u.getNombre() : "")
                        .apellido(u.getApellido() != null ? u.getApellido() : "")
                        .email(u.getEmail() != null ? u.getEmail() : "")
                        .telefono(u.getTelefono())
                        .rol(u.getRol())
                        .activo(Boolean.TRUE.equals(u.getActivo()))
                        .createdAt(u.getCreatedAt())
                        .updatedAt(u.getUpdatedAt())
                        .build();
            }
            return response;
        } catch (Exception e) {
            log.error("{}: error mapeando usuario id={}: {}", origen, u.getId(), e.getMessage(), e);
            throw new RuntimeException("Error al procesar el usuario id=" + u.getId(), e);
        }
    }

    public UsuarioResponse obtenerPorId(Long id, String emailSolicitante) {
        return usuarioMapper.toResponse(findEnAlcance(id, solicitante(emailSolicitante)));
    }

    public UsuarioResponse obtenerPorEmail(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsuarioNotFoundException(email));
        return usuarioMapper.toResponse(usuario);
    }

    @Transactional
    public UsuarioResponse actualizar(Long id, ActualizarUsuarioRequest request, String emailSolicitante) {
        Usuario solicitante = solicitante(emailSolicitante);
        Usuario usuario = findEnAlcance(id, solicitante);
        verificarPuedeGestionar(usuario, solicitante);
        usuarioMapper.updateEntityFromRequest(usuario, request);
        if (request.getNuevaPassword() != null && !request.getNuevaPassword().isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(request.getNuevaPassword()));
        }
        return usuarioMapper.toResponse(usuarioRepository.save(usuario));
    }

    /**
     * Cambia el rol de un usuario (p. ej. un empleado que entró con Google y quedó como HUESPED).
     * Un admin no puede cambiar su propio rol, para que el hotel no se quede sin administradores,
     * y solo un SUPER_ADMIN puede otorgar o quitar el rol SUPER_ADMIN.
     * Se cierran sus sesiones: el rol viaja en el JWT y así el cambio aplica en su próximo login.
     */
    @Transactional
    public UsuarioResponse cambiarRol(Long id, String rol, String emailSolicitante) {
        Usuario.Rol nuevoRol = parsearRol(rol == null ? "" : rol);
        Usuario solicitante = solicitante(emailSolicitante);
        Usuario usuario = findEnAlcance(id, solicitante);
        if (usuario.getId().equals(solicitante.getId())) {
            throw new IllegalArgumentException("No puedes cambiar tu propio rol.");
        }
        verificarPuedeGestionar(usuario, solicitante);
        if (nuevoRol == Usuario.Rol.SUPER_ADMIN && !esSuperAdmin(solicitante)) {
            throw new OperacionNoPermitidaException("Solo un super administrador puede otorgar ese rol.");
        }
        usuario.setRol(nuevoRol);
        refreshTokenRepository.deleteByUsuario(usuario);
        log.info("Rol de {} cambiado a {} por {}", usuario.getEmail(), nuevoRol, emailSolicitante);
        return usuarioMapper.toResponse(usuarioRepository.save(usuario));
    }

    /**
     * Mueve una cuenta de personal a otro hotel. Solo SUPER_ADMIN. Los huéspedes no se mueven:
     * sus reservas y pagos pertenecen al hotel donde se crearon.
     */
    @Transactional
    public UsuarioResponse cambiarHotel(Long id, String tenantId, String emailSolicitante) {
        Usuario solicitante = solicitante(emailSolicitante);
        if (!esSuperAdmin(solicitante)) {
            throw new OperacionNoPermitidaException("Solo un super administrador puede cambiar el hotel de un usuario.");
        }
        Usuario usuario = findActivoOrThrow(id);
        if (usuario.getRol() == Usuario.Rol.HUESPED) {
            throw new IllegalArgumentException(
                    "Solo se puede cambiar de hotel al personal (administradores y recepcionistas).");
        }
        String destino = hotelService.verificarActivo(tenantId);
        if (destino.equals(usuario.getTenantId())) {
            return usuarioMapper.toResponse(usuario);
        }
        String origen = usuario.getTenantId();
        usuario.setTenantId(destino);
        refreshTokenRepository.deleteByUsuario(usuario);
        log.info("Usuario {} movido del hotel {} a {} por {}", usuario.getEmail(), origen, destino, emailSolicitante);
        return usuarioMapper.toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public void desactivar(Long id, String emailSolicitante) {
        Usuario solicitante = solicitante(emailSolicitante);
        Usuario usuario = findEnAlcance(id, solicitante);
        verificarPuedeGestionar(usuario, solicitante);
        usuario.setActivo(false);
        refreshTokenRepository.deleteByUsuario(usuario);
        usuarioRepository.save(usuario);
        log.info("Usuario desactivado: {}", usuario.getEmail());
    }

    /**
     * Login vía Google: busca el usuario por email o lo crea si es la primera vez.
     * La contraseña se genera al azar y nunca se usa — la identidad ya la confirmó Google/Cognito.
     * El hotel solo se asigna al crear: un usuario existente conserva el suyo.
     */
    @Transactional
    public Usuario buscarOCrearPorGoogle(String email, String nombre, String apellido, String hotelSlug) {
        return usuarioRepository.findByEmail(email).orElseGet(() -> {
            Usuario nuevo = Usuario.builder()
                    .tenantId(hotelService.resolverTenantId(hotelSlug))
                    .nombre(nombre != null && !nombre.isBlank() ? nombre : "Usuario")
                    .apellido(apellido != null && !apellido.isBlank() ? apellido : "Google")
                    .email(email)
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .rol(Usuario.Rol.HUESPED)
                    .activo(true)
                    .build();
            Usuario guardado = usuarioRepository.save(nuevo);
            log.info("Usuario creado vía Google: {}", email);
            return guardado;
        });
    }

    private Usuario solicitante(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsuarioNotFoundException(email));
    }

    private static boolean esSuperAdmin(Usuario usuario) {
        return usuario.getRol() == Usuario.Rol.SUPER_ADMIN;
    }

    /** Usuario activo dentro del alcance del solicitante; si es de otro hotel, 404. */
    private Usuario findEnAlcance(Long id, Usuario solicitante) {
        Usuario usuario = findActivoOrThrow(id);
        if (!esSuperAdmin(solicitante) && !Objects.equals(usuario.getTenantId(), solicitante.getTenantId())) {
            throw new UsuarioNotFoundException(id);
        }
        return usuario;
    }

    /** Un admin de hotel no puede editar, degradar ni desactivar a un SUPER_ADMIN. */
    private static void verificarPuedeGestionar(Usuario objetivo, Usuario solicitante) {
        boolean esElMismo = objetivo.getId().equals(solicitante.getId());
        if (esSuperAdmin(objetivo) && !esSuperAdmin(solicitante) && !esElMismo) {
            throw new OperacionNoPermitidaException("No puedes modificar la cuenta de un super administrador.");
        }
    }

    private static Usuario.Rol parsearRol(String rol) {
        try {
            return Usuario.Rol.valueOf(rol.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Rol inválido: '" + rol + "'. Valores aceptados: "
                    + java.util.Arrays.toString(Usuario.Rol.values()));
        }
    }

    private Usuario findActivoOrThrow(Long id) {
        return usuarioRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new UsuarioNotFoundException(id));
    }
}
