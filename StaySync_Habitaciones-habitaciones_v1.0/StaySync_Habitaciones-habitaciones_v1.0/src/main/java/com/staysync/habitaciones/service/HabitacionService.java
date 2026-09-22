package com.staysync.habitaciones.service;

import com.staysync.habitaciones.client.OtaClient;
import com.staysync.habitaciones.dto.request.CrearHabitacionRequest;
import com.staysync.habitaciones.dto.response.AmenidadResponse;
import com.staysync.habitaciones.dto.response.HabitacionResponse;
import com.staysync.habitaciones.dto.response.TipoHabitacionResponse;
import com.staysync.habitaciones.exception.HabitacionNotFoundException;
import com.staysync.habitaciones.exception.HabitacionNumeroExistenteException;
import com.staysync.habitaciones.model.Amenidad;
import com.staysync.habitaciones.model.Habitacion;
import com.staysync.habitaciones.model.Habitacion.EstadoHabitacion;
import com.staysync.habitaciones.model.TipoHabitacion;
import com.staysync.habitaciones.repository.AmenidadRepository;
import com.staysync.habitaciones.repository.HabitacionRepository;
import com.staysync.habitaciones.repository.TipoHabitacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class HabitacionService {

    private final HabitacionRepository habitacionRepository;
    private final TipoHabitacionRepository tipoRepository;
    private final AmenidadRepository amenidadRepository;
    private final OtaClient otaClient;

    public List<HabitacionResponse> listarTodas() {
        return habitacionRepository.findByActivaTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public HabitacionResponse obtenerPorId(Long id) {
        return toResponse(findActivaOrThrow(id));
    }

    public HabitacionResponse obtenerPorNumero(String numero) {
        Habitacion h = habitacionRepository.findByNumero(numero)
                .orElseThrow(() -> new HabitacionNotFoundException(numero));
        return toResponse(h);
    }

    public List<HabitacionResponse> listarDisponibles() {
        return habitacionRepository.findByEstadoAndActivaTrue(EstadoHabitacion.DISPONIBLE).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<HabitacionResponse> buscarDisponibles(Integer capacidad, String amenidad, String sort) {
        // Si se filtra por amenidad cargamos el collection con JOIN FETCH para evitar N+1
        List<Habitacion> base = (amenidad != null && !amenidad.isBlank())
                ? habitacionRepository.findDisponiblesConAmenidades()
                : habitacionRepository.findByEstadoAndActivaTrue(EstadoHabitacion.DISPONIBLE);

        Stream<Habitacion> stream = base.stream();

        if (capacidad != null && capacidad > 0) {
            stream = stream.filter(h -> h.getTipo() != null && h.getTipo().getCapacidad() >= capacidad);
        }

        if (amenidad != null && !amenidad.isBlank()) {
            String q = amenidad.toLowerCase();
            stream = stream.filter(h -> h.getAmenidades().stream()
                    .anyMatch(a -> a.getNombre().toLowerCase().contains(q)));
        }

        Comparator<Habitacion> orden = "precio_desc".equalsIgnoreCase(sort)
                ? Comparator.comparing(Habitacion::getPrecioPorNoche).reversed()
                : Comparator.comparing(Habitacion::getPrecioPorNoche);

        return stream.sorted(orden).map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public HabitacionResponse crear(CrearHabitacionRequest request) {
        if (habitacionRepository.existsByNumero(request.getNumero())) {
            throw new HabitacionNumeroExistenteException(request.getNumero());
        }
        TipoHabitacion tipo = tipoRepository.findById(request.getTipoId())
                .orElseThrow(() -> new HabitacionNotFoundException(request.getTipoId()));

        Set<Amenidad> amenidades = request.getAmenidadIds() != null
                ? amenidadRepository.findAllById(request.getAmenidadIds()).stream().collect(Collectors.toSet())
                : Set.of();

        Habitacion habitacion = Habitacion.builder()
                .numero(request.getNumero())
                .piso(request.getPiso())
                .tipo(tipo)
                .descripcion(request.getDescripcion())
                .precioPorNoche(request.getPrecioPorNoche())
                .estado(EstadoHabitacion.DISPONIBLE)
                .amenidades(amenidades)
                .build();

        Habitacion guardada = habitacionRepository.save(habitacion);
        log.info("Habitación creada: {}", guardada.getNumero());
        return toResponse(guardada);
    }

    @Transactional
    public HabitacionResponse cambiarEstado(Long id, EstadoHabitacion nuevoEstado) {
        Habitacion habitacion = findActivaOrThrow(id);
        EstadoHabitacion estadoAnterior = habitacion.getEstado();
        habitacion.setEstado(nuevoEstado);
        Habitacion guardada = habitacionRepository.save(habitacion);

        otaClient.sincronizarEstado(id, estadoAnterior.name(), nuevoEstado.name());
        log.info("Habitación {} cambió estado: {} -> {}", id, estadoAnterior, nuevoEstado);
        return toResponse(guardada);
    }

    public List<TipoHabitacionResponse> listarTiposActivos() {
        return tipoRepository.findByActivoTrue().stream()
                .map(t -> TipoHabitacionResponse.builder()
                        .id(t.getId())
                        .nombre(t.getNombre())
                        .descripcion(t.getDescripcion())
                        .capacidad(t.getCapacidad())
                        .precioBase(t.getPrecioBase())
                        .build())
                .collect(Collectors.toList());
    }

    public List<AmenidadResponse> listarAmenidades() {
        return amenidadRepository.findAll().stream()
                .map(a -> AmenidadResponse.builder()
                        .id(a.getId())
                        .nombre(a.getNombre())
                        .descripcion(a.getDescripcion())
                        .icono(a.getIcono())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void desactivar(Long id) {
        Habitacion habitacion = findActivaOrThrow(id);
        habitacion.setActiva(false);
        habitacionRepository.save(habitacion);
    }

    private Habitacion findActivaOrThrow(Long id) {
        return habitacionRepository.findByIdAndActivaTrue(id)
                .orElseThrow(() -> new HabitacionNotFoundException(id));
    }

    private HabitacionResponse toResponse(Habitacion h) {
        return HabitacionResponse.builder()
                .id(h.getId())
                .numero(h.getNumero())
                .piso(h.getPiso())
                .tipoNombre(h.getTipo() != null ? h.getTipo().getNombre() : null)
                .capacidad(h.getTipo() != null ? h.getTipo().getCapacidad() : null)
                .estado(h.getEstado())
                .descripcion(h.getDescripcion())
                .precioPorNoche(h.getPrecioPorNoche())
                .activa(h.getActiva())
                .createdAt(h.getCreatedAt())
                .updatedAt(h.getUpdatedAt())
                .build();
    }
}
