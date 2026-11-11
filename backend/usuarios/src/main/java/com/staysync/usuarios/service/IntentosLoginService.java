package com.staysync.usuarios.service;

import com.staysync.usuarios.exception.CuentaBloqueadaException;
import com.staysync.usuarios.model.Usuario;
import com.staysync.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Bloqueo temporal de la cuenta tras varios intentos de login fallidos seguidos. Se guarda en
 * la base de datos, así que vale para todas las instancias y frena también los ataques
 * repartidos entre muchas IPs (el límite por IP lo aplica el BFF).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntentosLoginService {

    private final UsuarioRepository usuarioRepository;

    @Value("${seguridad.login.max-intentos:5}")
    private int maxIntentos;

    @Value("${seguridad.login.bloqueo:15m}")
    private Duration duracionBloqueo;

    /** Lanza {@link CuentaBloqueadaException} si la cuenta sigue bloqueada. */
    @Transactional(readOnly = true)
    public void verificarNoBloqueada(String email) {
        LocalDateTime ahora = ahora();
        usuarioRepository.findByEmail(email)
                .map(Usuario::getBloqueadoHasta)
                .filter(hasta -> hasta.isAfter(ahora))
                .ifPresent(hasta -> {
                    throw new CuentaBloqueadaException(minutosHasta(ahora, hasta));
                });
    }

    /**
     * Suma un intento fallido y bloquea la cuenta al llegar al máximo. Va en su propia
     * transacción: el login que falla se revierte, el contador no. La fila se bloquea para que
     * dos intentos simultáneos no se pisen. Un email que no existe no deja rastro.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarFallo(String email) {
        usuarioRepository.findByEmailParaActualizar(email).ifPresent(usuario -> {
            int intentos = (usuario.getIntentosFallidos() == null ? 0 : usuario.getIntentosFallidos()) + 1;
            if (intentos >= maxIntentos) {
                usuario.setIntentosFallidos(0);
                usuario.setBloqueadoHasta(ahora().plus(duracionBloqueo));
                log.warn("Cuenta {} bloqueada {} min tras {} intentos de login fallidos",
                        usuario.getEmail(), duracionBloqueo.toMinutes(), intentos);
            } else {
                usuario.setIntentosFallidos(intentos);
            }
        });
    }

    /** Tras un login correcto el contador vuelve a cero. Se une a la transacción del login. */
    @Transactional
    public void reiniciar(Usuario usuario) {
        boolean conIntentos = usuario.getIntentosFallidos() != null && usuario.getIntentosFallidos() > 0;
        if (conIntentos || usuario.getBloqueadoHasta() != null) {
            usuario.setIntentosFallidos(0);
            usuario.setBloqueadoHasta(null);
            usuarioRepository.save(usuario);
        }
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }

    private static long minutosHasta(LocalDateTime ahora, LocalDateTime hasta) {
        long segundos = Duration.between(ahora, hasta).toSeconds();
        return Math.max(1, (segundos + 59) / 60);
    }
}
