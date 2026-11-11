package com.staysync.habitaciones.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staysync.eventos.EventoDominio;
import com.staysync.eventos.ProcesadorEventos;
import com.staysync.eventos.TiposEvento;
import com.staysync.habitaciones.client.OtaClient;
import com.staysync.habitaciones.messaging.EventosHabitacionListener;
import com.staysync.habitaciones.repository.TipoHabitacionRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Aislamiento entre hoteles y consumo de eventos, contra H2 con Hibernate real. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:habitaciones_mt;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@DisplayName("Habitaciones - aislamiento por hotel")
class HabitacionesMultiTenantTest {

    private static final String DEMO = "staysync-demo";
    private static final String ANDES = "hotel-andes";

    @Autowired MockMvc mockMvc;
    @Autowired TipoHabitacionRepository tipoRepository;
    @Autowired ProcesadorEventos procesador;
    @Autowired EventosHabitacionListener listener;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean OtaClient otaClient;

    private Long crear(String hotel, String numero) throws Exception {
        Long tipoId = tipoRepository.findAll().get(0).getId();
        String body = """
                {"numero":"%s","piso":1,"tipoId":%d,"precioPorNoche":50000}""".formatted(numero, tipoId);
        String resp = mockMvc.perform(post("/api/v1/habitaciones").header("X-Tenant-Id", hotel)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resp, "$.id")).longValue();
    }

    @Test
    @DisplayName("Dos hoteles pueden tener la misma habitación 101; cada uno ve solo la suya")
    void mismoNumeroEnDosHoteles() throws Exception {
        Long idDemo = crear(DEMO, "101");
        Long idAndes = crear(ANDES, "101");

        mockMvc.perform(get("/api/v1/habitaciones").header("X-Tenant-Id", DEMO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(idDemo.intValue())))
                .andExpect(jsonPath("$[*].id", not(hasItem(idAndes.intValue()))));

        // La habitación de Andes no existe para StaySync Demo
        mockMvc.perform(get("/api/v1/habitaciones/" + idAndes).header("X-Tenant-Id", DEMO))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/habitaciones/" + idAndes).header("X-Tenant-Id", ANDES))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Dentro de un mismo hotel el número sigue siendo único")
    void numeroDuplicadoEnMismoHotel() throws Exception {
        crear(DEMO, "202");
        Long tipoId = tipoRepository.findAll().get(0).getId();
        mockMvc.perform(post("/api/v1/habitaciones").header("X-Tenant-Id", DEMO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numero":"202","piso":2,"tipoId":%d,"precioPorNoche":50000}""".formatted(tipoId)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Sin X-Tenant-Id la petición se rechaza")
    void sinHotelSeRechaza() throws Exception {
        mockMvc.perform(get("/api/v1/habitaciones")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("ReservaCheckin y ReservaCheckout cambian el estado de la habitación del hotel del evento")
    void eventosCambianEstado() throws Exception {
        Long id = crear(ANDES, "303");

        String checkin = objectMapper.writeValueAsString(
                EventoDominio.nuevo(TiposEvento.RESERVA_CHECKIN, ANDES, Map.of("habitacionId", id)));
        listener.recibir(checkin);
        mockMvc.perform(get("/api/v1/habitaciones/" + id).header("X-Tenant-Id", ANDES))
                .andExpect(jsonPath("$.estado").value("OCUPADA"));

        listener.recibir(objectMapper.writeValueAsString(
                EventoDominio.nuevo(TiposEvento.RESERVA_CHECKOUT, ANDES, Map.of("habitacionId", id))));
        mockMvc.perform(get("/api/v1/habitaciones/" + id).header("X-Tenant-Id", ANDES))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"));
    }
}
