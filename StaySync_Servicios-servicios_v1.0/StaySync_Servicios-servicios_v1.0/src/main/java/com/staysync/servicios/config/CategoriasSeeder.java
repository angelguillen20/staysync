package com.staysync.servicios.config;

import com.staysync.servicios.model.CategoriaServicio;
import com.staysync.servicios.repository.CategoriaServicioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Las categorías son comunes a todos los hoteles y no tienen endpoint de alta: sin este
 * seeder, en una base nueva el admin no tendría dónde clasificar un servicio y no podría
 * crearlo. Se precargan una sola vez, igual que el catálogo de habitaciones.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CategoriasSeeder implements CommandLineRunner {

    private final CategoriaServicioRepository categoriaRepository;

    @Override
    public void run(String... args) {
        if (categoriaRepository.count() > 0) {
            return;
        }
        categoriaRepository.saveAll(List.of(
                categoria("Alimentación", "Desayuno, room service y bebidas"),
                categoria("Bienestar", "Spa, masajes y gimnasio"),
                categoria("Transporte", "Traslados, estacionamiento y arriendo"),
                categoria("Lavandería", "Lavado, planchado y tintorería"),
                categoria("Experiencias", "Tours y actividades"),
                categoria("Estadía", "Early check-in, late check-out, cama extra, mascotas")));
        log.info("Categorías de servicios precargadas (6).");
    }

    private static CategoriaServicio categoria(String nombre, String descripcion) {
        return CategoriaServicio.builder().nombre(nombre).descripcion(descripcion).build();
    }
}
