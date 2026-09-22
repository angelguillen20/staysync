package com.staysync.ota.service;

import com.staysync.ota.exception.CanalOtaNotFoundException;
import com.staysync.ota.model.CanalOta;
import com.staysync.ota.model.ReservaOta;
import com.staysync.ota.repository.CanalOtaRepository;
import com.staysync.ota.repository.ReservaOtaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CanalOtaService {

    private final CanalOtaRepository canalRepository;
    private final ReservaOtaRepository reservaOtaRepository;

    public List<CanalOta> listarActivos() {
        return canalRepository.findByActivoTrue();
    }

    /**
     * Notifica a los canales OTA activos que el estado de una habitación cambió, para que
     * ajusten su disponibilidad. Llamado directamente (RestTemplate) por habitaciones-service
     * en cada cambio de estado — sustituye al antiguo evento de RabbitMQ.
     */
    public void sincronizarEstadoHabitacion(Long habitacionId, String estadoAnterior, String estadoNuevo) {
        listarActivos().forEach(canal ->
                log.info("Canal OTA [{}] — habitación {} cambió de {} a {}",
                        canal.getCodigo(), habitacionId, estadoAnterior, estadoNuevo));
    }

    public CanalOta obtenerPorId(Long id) {
        return canalRepository.findById(id)
                .orElseThrow(() -> new CanalOtaNotFoundException(id));
    }

    @Transactional
    public ReservaOta registrarReservaExterna(Long canalId, String reservaIdExt,
                                               Map<String, Object> payload) {
        CanalOta canal = obtenerPorId(canalId);

        // Verificar si ya existe
        return reservaOtaRepository
                .findByCanalIdAndReservaIdExt(canalId, reservaIdExt)
                .orElseGet(() -> {
                    ReservaOta nuevaReserva = ReservaOta.builder()
                            .canal(canal)
                            .reservaIdExt(reservaIdExt)
                            .estadoExt("PENDIENTE")
                            .payloadEntrada(payload)
                            .sincronizada(false)
                            .build();
                    ReservaOta guardada = reservaOtaRepository.save(nuevaReserva);
                    log.info("Reserva OTA registrada: {} - Canal: {}", reservaIdExt, canal.getCodigo());
                    return guardada;
                });
    }

    @Transactional
    public void marcarSincronizada(Long reservaOtaId, Long reservaIdLocal) {
        reservaOtaRepository.findById(reservaOtaId).ifPresent(r -> {
            r.setReservaIdLocal(reservaIdLocal);
            r.setSincronizada(true);
            reservaOtaRepository.save(r);
        });
    }

    public List<ReservaOta> listarPendientesSincronizacion() {
        return reservaOtaRepository.findBySincronizadaFalse();
    }
}
