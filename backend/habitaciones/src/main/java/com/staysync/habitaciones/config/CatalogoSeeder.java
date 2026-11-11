package com.staysync.habitaciones.config;

import com.staysync.habitaciones.model.Amenidad;
import com.staysync.habitaciones.model.TipoHabitacion;
import com.staysync.habitaciones.repository.AmenidadRepository;
import com.staysync.habitaciones.repository.TipoHabitacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * No existe ningún endpoint para crear tipos de habitación ni amenidades (son catálogos
 * de solo lectura vía API), así que en una base de datos nueva ambas tablas quedan vacías
 * y el formulario de "Nueva habitación" del admin no tiene qué ofrecer en el selector de
 * tipo, bloqueando la creación de habitaciones. Este seeder los precarga una sola vez.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogoSeeder implements CommandLineRunner {

    private final TipoHabitacionRepository tipoHabitacionRepository;
    private final AmenidadRepository amenidadRepository;

    @Override
    public void run(String... args) {
        if (tipoHabitacionRepository.count() == 0) {
            tipoHabitacionRepository.saveAll(List.of(
                    TipoHabitacion.builder().nombre("Individual").descripcion("Una cama individual, ideal para viajeros solos.").capacidad(1).precioBase(new BigDecimal("45000")).build(),
                    TipoHabitacion.builder().nombre("Doble").descripcion("Una cama matrimonial o dos individuales.").capacidad(2).precioBase(new BigDecimal("65000")).build(),
                    TipoHabitacion.builder().nombre("Suite").descripcion("Habitación amplia con sala de estar independiente.").capacidad(2).precioBase(new BigDecimal("120000")).build(),
                    TipoHabitacion.builder().nombre("Familiar").descripcion("Varias camas, pensada para grupos o familias.").capacidad(4).precioBase(new BigDecimal("150000")).build()
            ));
            log.info("Catálogo de tipos de habitación precargado (4 tipos).");
        }

        if (amenidadRepository.count() == 0) {
            amenidadRepository.saveAll(List.of(
                    Amenidad.builder().nombre("WiFi").descripcion("Internet inalámbrico de alta velocidad").icono("bi-wifi").build(),
                    Amenidad.builder().nombre("TV").descripcion("Televisión con cable/streaming").icono("bi-tv").build(),
                    Amenidad.builder().nombre("Aire acondicionado").descripcion("Climatización individual").icono("bi-snow").build(),
                    Amenidad.builder().nombre("Minibar").descripcion("Refrigerador con bebidas y snacks").icono("bi-cup-straw").build(),
                    Amenidad.builder().nombre("Vista al mar").descripcion("Ventana o balcón con vista al mar").icono("bi-water").build()
            ));
            log.info("Catálogo de amenidades precargado (5 amenidades).");
        }
    }
}
