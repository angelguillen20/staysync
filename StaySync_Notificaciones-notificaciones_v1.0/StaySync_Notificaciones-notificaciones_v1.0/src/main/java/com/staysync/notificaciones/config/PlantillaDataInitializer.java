package com.staysync.notificaciones.config;

import com.staysync.notificaciones.model.Notificacion;
import com.staysync.notificaciones.model.PlantillaNotificacion;
import com.staysync.notificaciones.repository.PlantillaNotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlantillaDataInitializer implements ApplicationRunner {

    private final PlantillaNotificacionRepository plantillaRepository;

    @Override
    public void run(ApplicationArguments args) {
        seedPlantilla(
                "USUARIO_REGISTRO",
                "Bienvenida nuevo usuario",
                "Bienvenido a StaySync, {{nombre}}",
                """
                Hola {{nombre}},

                Tu cuenta en StaySync ha sido creada exitosamente.
                Correo registrado: {{email}}

                Ya puedes iniciar sesión y explorar nuestras habitaciones disponibles.

                Si no creaste esta cuenta, ignora este mensaje o contáctanos.

                ¡Te esperamos pronto!
                — Equipo StaySync
                """);

        seedPlantilla(
                "RESERVA_CREADA",
                "Confirmación de reserva",
                "Reserva registrada #{{codigo}} - StaySync",
                """
                Hola {{nombreUsuario}},

                Tu reserva ha sido registrada correctamente:

                  Código:      {{codigo}}
                  Habitación:  {{habitacion}}
                  Entrada:     {{fechaEntrada}}
                  Salida:      {{fechaSalida}}
                  Total:       ${{precioTotal}}

                Conserva este código para cualquier consulta.

                ¡Nos vemos pronto!
                — Equipo StaySync
                """);

        seedPlantilla(
                "RESERVA_ESTADO",
                "Actualización de estado de reserva",
                "Tu reserva #{{codigo}} ha sido actualizada - StaySync",
                """
                Hola {{nombreUsuario}},

                El estado de tu reserva ha cambiado:

                  Código:      {{codigo}}
                  Habitación:  {{habitacion}}
                  Nuevo estado: {{estadoNuevo}}

                Si tienes alguna duda sobre este cambio, contáctanos.

                — Equipo StaySync
                """);

        log.info("Plantillas de notificación verificadas.");
    }

    private void seedPlantilla(String codigo, String nombre, String asunto, String cuerpo) {
        if (plantillaRepository.findByCodigo(codigo).isEmpty()) {
            PlantillaNotificacion plantilla = PlantillaNotificacion.builder()
                    .codigo(codigo)
                    .nombre(nombre)
                    .canal(Notificacion.Canal.EMAIL)
                    .asunto(asunto)
                    .cuerpo(cuerpo)
                    .activa(true)
                    .build();
            plantillaRepository.save(plantilla);
            log.info("Plantilla '{}' creada.", codigo);
        }
    }
}
