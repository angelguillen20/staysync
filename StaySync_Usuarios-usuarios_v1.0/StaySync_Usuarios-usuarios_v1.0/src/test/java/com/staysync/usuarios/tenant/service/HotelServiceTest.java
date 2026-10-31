package com.staysync.usuarios.tenant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staysync.usuarios.tenant.dto.HotelResponse;
import com.staysync.usuarios.tenant.exception.HotelNotFoundException;
import com.staysync.usuarios.tenant.model.Hotel;
import com.staysync.usuarios.tenant.repository.HotelRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HotelService - Tests Unitarios")
class HotelServiceTest {

    private static final String DEFAULT_TENANT = "staysync-demo";

    @Mock private HotelRepository hotelRepository;

    private HotelService hotelService;
    private Hotel andes;

    @BeforeEach
    void setUp() {
        hotelService = new HotelService(hotelRepository, new ObjectMapper(), DEFAULT_TENANT);
        andes = Hotel.builder()
                .tenantId("hotel-andes")
                .slug("hotel-andes")
                .nombre("Hotel Andes")
                .logoUrl("https://cdn.example.com/andes.png")
                .theme("{\"primary\":\"#7A2E1F\",\"secondary\":\"#2F6B4F\"}")
                .activo(true)
                .build();
    }

    @Nested
    @DisplayName("crear()")
    class CrearTests {

        @Test
        @DisplayName("Crea el hotel con el slug como tenantId y la paleta de StaySync si no trae colores")
        void creaConPaletaPorDefecto() {
            when(hotelRepository.existsBySlug("hotel-pacifico")).thenReturn(false);
            when(hotelRepository.existsById("hotel-pacifico")).thenReturn(false);
            when(hotelRepository.save(any(Hotel.class))).thenAnswer(inv -> inv.getArgument(0));

            HotelResponse creado = hotelService.crear("Hotel Pacífico ", "Hotel-Pacifico", "", null);

            assertThat(creado.getTenantId()).isEqualTo("hotel-pacifico");
            assertThat(creado.getSlug()).isEqualTo("hotel-pacifico");
            assertThat(creado.getNombre()).isEqualTo("Hotel Pacífico");
            assertThat(creado.getLogoUrl()).isNull();
            assertThat(creado.getTheme()).containsEntry("primary", "#222016");
        }

        @Test
        @DisplayName("Rechaza un portal que ya existe")
        void rechazaSlugRepetido() {
            when(hotelRepository.existsBySlug("hotel-andes")).thenReturn(true);

            assertThatThrownBy(() -> hotelService.crear("Otro", "hotel-andes", null, null))
                    .isInstanceOf(com.staysync.usuarios.tenant.exception.HotelDuplicadoException.class);
            verify(hotelRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("resolverTenantId()")
    class ResolverTenantIdTests {

        @Test
        @DisplayName("Sin slug debe devolver el hotel por defecto")
        void sinSlugDevuelveDefault() {
            assertThat(hotelService.resolverTenantId(null)).isEqualTo(DEFAULT_TENANT);
            assertThat(hotelService.resolverTenantId("  ")).isEqualTo(DEFAULT_TENANT);
            verifyNoInteractions(hotelRepository);
        }

        @Test
        @DisplayName("Con slug existente debe devolver su tenantId")
        void conSlugExistente() {
            when(hotelRepository.findBySlugAndActivoTrue("hotel-andes")).thenReturn(Optional.of(andes));

            assertThat(hotelService.resolverTenantId(" Hotel-Andes ")).isEqualTo("hotel-andes");
        }

        @Test
        @DisplayName("Con slug inexistente debe lanzar HotelNotFoundException")
        void conSlugInexistente() {
            when(hotelRepository.findBySlugAndActivoTrue("no-existe")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> hotelService.resolverTenantId("no-existe"))
                    .isInstanceOf(HotelNotFoundException.class)
                    .hasMessageContaining("no-existe");
        }
    }

    @Test
    @DisplayName("obtenerPorSlug() debe devolver nombre, logo y theme parseado")
    void obtenerPorSlugParseaTheme() {
        when(hotelRepository.findBySlugAndActivoTrue("hotel-andes")).thenReturn(Optional.of(andes));

        HotelResponse response = hotelService.obtenerPorSlug("hotel-andes");

        assertThat(response.getTenantId()).isEqualTo("hotel-andes");
        assertThat(response.getNombre()).isEqualTo("Hotel Andes");
        assertThat(response.getLogoUrl()).isEqualTo("https://cdn.example.com/andes.png");
        assertThat(response.getTheme())
                .containsEntry("primary", "#7A2E1F")
                .containsEntry("secondary", "#2F6B4F");
    }

    @Test
    @DisplayName("Un theme con JSON inválido no debe romper la respuesta")
    void themeInvalidoDevuelveVacio() {
        andes.setTheme("{no es json");
        when(hotelRepository.findByTenantIdAndActivoTrue("hotel-andes")).thenReturn(Optional.of(andes));

        Optional<HotelResponse> response = hotelService.buscarPorTenantId("hotel-andes");

        assertThat(response).isPresent();
        assertThat(response.get().getTheme()).isEmpty();
    }

    @Test
    @DisplayName("buscarPorTenantId(null) debe devolver vacío sin consultar la BD")
    void buscarPorTenantIdNulo() {
        assertThat(hotelService.buscarPorTenantId(null)).isEmpty();
        verifyNoInteractions(hotelRepository);
    }
}
