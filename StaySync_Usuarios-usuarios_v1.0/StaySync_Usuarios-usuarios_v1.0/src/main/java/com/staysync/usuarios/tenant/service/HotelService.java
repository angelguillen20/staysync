package com.staysync.usuarios.tenant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staysync.usuarios.tenant.dto.HotelResponse;
import com.staysync.usuarios.tenant.exception.HotelNotFoundException;
import com.staysync.usuarios.tenant.model.Hotel;
import com.staysync.usuarios.tenant.repository.HotelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@Transactional(readOnly = true)
public class HotelService {

    private static final TypeReference<Map<String, String>> THEME_TYPE = new TypeReference<>() {};

    private final HotelRepository hotelRepository;
    private final ObjectMapper objectMapper;
    private final String defaultTenantId;

    public HotelService(HotelRepository hotelRepository,
                        ObjectMapper objectMapper,
                        @Value("${staysync.tenant.default-id:staysync-demo}") String defaultTenantId) {
        this.hotelRepository = hotelRepository;
        this.objectMapper = objectMapper;
        this.defaultTenantId = defaultTenantId;
    }

    public String getDefaultTenantId() {
        return defaultTenantId;
    }

    /**
     * Hotel al que se asigna un usuario nuevo: el del portal desde el que se registra
     * (slug) o, si no se indica, el hotel por defecto.
     */
    public String resolverTenantId(String hotelSlug) {
        if (hotelSlug == null || hotelSlug.isBlank()) {
            return defaultTenantId;
        }
        return hotelRepository.findBySlugAndActivoTrue(hotelSlug.trim().toLowerCase())
                .map(Hotel::getTenantId)
                .orElseThrow(() -> new HotelNotFoundException(hotelSlug));
    }

    /** Endpoint público: la pantalla de login lo usa para pintar la marca antes de autenticar. */
    public HotelResponse obtenerPorSlug(String slug) {
        return hotelRepository.findBySlugAndActivoTrue(slug.trim().toLowerCase())
                .map(this::toResponse)
                .orElseThrow(() -> new HotelNotFoundException(slug));
    }

    /** Datos del hotel para la respuesta de login; vacío si el hotel fue desactivado. */
    public Optional<HotelResponse> buscarPorTenantId(String tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        return hotelRepository.findByTenantIdAndActivoTrue(tenantId).map(this::toResponse);
    }

    /** Para el selector de hotel de la pantalla de usuarios (solo SUPER_ADMIN). */
    public List<HotelResponse> listarActivos() {
        return hotelRepository.findAllByActivoTrueOrderByNombreAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    /** Devuelve el tenantId si corresponde a un hotel activo; si no, HotelNotFoundException. */
    public String verificarActivo(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Debes indicar el hotel (tenantId).");
        }
        return hotelRepository.findByTenantIdAndActivoTrue(tenantId.trim())
                .map(Hotel::getTenantId)
                .orElseThrow(() -> new HotelNotFoundException(tenantId));
    }

    private HotelResponse toResponse(Hotel hotel) {
        return HotelResponse.builder()
                .tenantId(hotel.getTenantId())
                .nombre(hotel.getNombre())
                .slug(hotel.getSlug())
                .logoUrl(hotel.getLogoUrl())
                .theme(parsearTheme(hotel))
                .build();
    }

    private Map<String, String> parsearTheme(Hotel hotel) {
        if (hotel.getTheme() == null || hotel.getTheme().isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(hotel.getTheme(), THEME_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Theme inválido para el hotel {}: {}", hotel.getTenantId(), e.getOriginalMessage());
            return Collections.emptyMap();
        }
    }
}
