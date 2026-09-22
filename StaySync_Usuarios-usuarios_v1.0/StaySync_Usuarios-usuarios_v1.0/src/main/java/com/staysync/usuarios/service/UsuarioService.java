package com.staysync.usuarios.service;

import com.staysync.usuarios.dto.request.ActualizarUsuarioRequest;
import com.staysync.usuarios.dto.request.RegistroRequest;
import com.staysync.usuarios.dto.response.UsuarioResponse;
import com.staysync.usuarios.exception.EmailDuplicadoException;
import com.staysync.usuarios.exception.UsuarioNotFoundException;
import com.staysync.usuarios.mapper.UsuarioMapper;
import com.staysync.usuarios.model.Usuario;
import com.staysync.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper usuarioMapper;

    @Transactional
    public UsuarioResponse registrar(RegistroRequest request) {
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new EmailDuplicadoException(request.getEmail());
        }
        Usuario usuario = usuarioMapper.toEntity(request);
        usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        Usuario guardado = usuarioRepository.save(usuario);
        log.info("Usuario registrado: {}", guardado.getEmail());
        return usuarioMapper.toResponse(guardado);
    }

    public Page<UsuarioResponse> listarTodos(Pageable pageable) {
        Pageable safePageable = (pageable != null) ? pageable : Pageable.unpaged();
        return usuarioRepository.findAll(safePageable).map(u -> mapearSeguro(u, "listarTodos"));
    }

    public List<UsuarioResponse> listarHuespedes() {
        log.debug("listarHuespedes: buscando todos los huéspedes activos");
        return buscar("", "HUESPED");
    }

    public List<UsuarioResponse> buscar(String q, String rol) {
        log.debug("buscar: q='{}', rol='{}'", q, rol);

        Specification<Usuario> spec = (root, query, cb) -> cb.isTrue(root.get("activo"));

        if (rol != null && !rol.isBlank()) {
            try {
                Usuario.Rol rolEnum = Usuario.Rol.valueOf(rol.trim().toUpperCase());
                spec = spec.and((root, query, cb) -> cb.equal(root.get("rol"), rolEnum));
            } catch (IllegalArgumentException e) {
                log.warn("buscar: rol inválido '{}' — roles válidos: {}", rol,
                        java.util.Arrays.toString(Usuario.Rol.values()));
                throw new IllegalArgumentException(
                        "Rol inválido: '" + rol + "'. Valores aceptados: " +
                        java.util.Arrays.toString(Usuario.Rol.values()));
            }
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
                        .nombre(u.getNombre() != null ? u.getNombre() : "")
                        .apellido(u.getApellido() != null ? u.getApellido() : "")
                        .email(u.getEmail() != null ? u.getEmail() : "")
                        .telefono(u.getTelefono())
                        .rol(u.getRol())
                        .activo(u.getActivo() != null ? u.getActivo() : false)
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

    public UsuarioResponse obtenerPorId(Long id) {
        return usuarioMapper.toResponse(findActivoOrThrow(id));
    }

    public UsuarioResponse obtenerPorEmail(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsuarioNotFoundException(email));
        return usuarioMapper.toResponse(usuario);
    }

    @Transactional
    public UsuarioResponse actualizar(Long id, ActualizarUsuarioRequest request) {
        Usuario usuario = findActivoOrThrow(id);
        usuarioMapper.updateEntityFromRequest(usuario, request);
        if (request.getNuevaPassword() != null && !request.getNuevaPassword().isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(request.getNuevaPassword()));
        }
        return usuarioMapper.toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public void desactivar(Long id) {
        Usuario usuario = findActivoOrThrow(id);
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
        log.info("Usuario desactivado: {}", usuario.getEmail());
    }

    private Usuario findActivoOrThrow(Long id) {
        return usuarioRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new UsuarioNotFoundException(id));
    }
}
