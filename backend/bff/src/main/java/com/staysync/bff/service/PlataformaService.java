package com.staysync.bff.service;

import com.staysync.bff.client.UsuariosClient;
import com.staysync.bff.exception.AccesoDenegadoException;
import com.staysync.bff.security.HotelVista;
import com.staysync.bff.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Supplier;

/**
 * Vista de plataforma del SUPER_ADMIN: repite una consulta en cada hotel (hotel = "todos") o en
 * uno (hotel = tenantId) y junta los resultados, anotando en cada fila a qué hotel pertenece.
 * Solo lectura; los cambios se hacen desde cada hotel.
 */
@Component
@RequiredArgsConstructor
public class PlataformaService {

    private final UsuariosClient usuariosClient;
    private final JwtService jwtService;

    public List<Map<String, Object>> porHotel(String authHeader, String hotel, Supplier<Object> consulta) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        if (!"SUPER_ADMIN".equals(jwtService.extractRol(token))) {
            throw new AccesoDenegadoException("Solo la plataforma ve datos de otros hoteles.");
        }
        List<Map<String, Object>> hoteles = lista(usuariosClient.listarHoteles(authHeader).getBody()).stream()
                .filter(h -> "todos".equals(hotel) || hotel.equals(h.get("tenantId")))
                .toList();
        if (hoteles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el hotel " + hotel + ".");
        }
        List<Map<String, Object>> todas = new ArrayList<>();
        for (Map<String, Object> h : hoteles) {
            String tenantId = String.valueOf(h.get("tenantId"));
            for (Map<String, Object> fila : lista(HotelVista.con(tenantId, consulta))) {
                Map<String, Object> conHotel = new LinkedHashMap<>(fila);
                conHotel.put("hotelId", tenantId);
                conHotel.put("hotelNombre", h.get("nombre"));
                todas.add(conHotel);
            }
        }
        return todas;
    }

    /** Acepta tanto una lista como una página de Spring ({ content: [...] }). */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> lista(Object body) {
        if (body instanceof Map<?, ?> pagina && pagina.get("content") instanceof List<?> l) {
            return (List<Map<String, Object>>) l;
        }
        return body instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }
}
