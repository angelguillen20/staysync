package com.staysync.tenant.jpa;

import com.staysync.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba el filtrado automático por hotel con Hibernate real (H2): es la garantía central
 * del multi-tenant, así que se prueba contra la base y no con mocks.
 */
@SpringBootTest(classes = TestJpaApp.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:tenantjpa;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "staysync.tenant.default-id=staysync-demo"
})
@DisplayName("Filtrado por hotel con @TenantId")
class TenantJpaIntegrationTest {

    @Autowired CuartoRepository repo;
    @Autowired JdbcTemplate jdbc;

    private Long idAndes;

    @BeforeEach
    void datos() {
        TenantContext.ejecutarComoSistema(() -> repo.deleteAll());
        TenantContext.ejecutarCon("staysync-demo", () -> repo.save(new Cuarto("101")));
        idAndes = TenantContext.ejecutarCon("hotel-andes", () -> repo.save(new Cuarto("101")).id);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Al guardar se asigna el tenant_id del contexto")
    void asignaTenantAlGuardar() {
        List<String> tenants = jdbc.queryForList("SELECT tenant_id FROM cuartos ORDER BY tenant_id", String.class);
        assertThat(tenants).containsExactly("hotel-andes", "staysync-demo");
    }

    @Test
    @DisplayName("findAll solo devuelve las filas del hotel actual")
    void findAllFiltra() {
        List<Cuarto> deDemo = TenantContext.ejecutarCon("staysync-demo", () -> repo.findAll());
        assertThat(deDemo).hasSize(1).allMatch(c -> "staysync-demo".equals(c.tenantId));
    }

    @Test
    @DisplayName("findById de una fila de otro hotel no la encuentra")
    void findByIdDeOtroHotel() {
        boolean visible = TenantContext.ejecutarCon("staysync-demo", () -> repo.findById(idAndes).isPresent());
        assertThat(visible).isFalse();
        boolean propio = TenantContext.ejecutarCon("hotel-andes", () -> repo.findById(idAndes).isPresent());
        assertThat(propio).isTrue();
    }

    @Test
    @DisplayName("Sin hotel en contexto no se ve nada")
    void sinHotelNoVeNada() {
        assertThat(repo.findAll()).isEmpty();
    }

    @Test
    @DisplayName("En modo sistema se ven todos los hoteles")
    void modoSistemaVeTodo() {
        assertThat(TenantContext.ejecutarComoSistema(() -> repo.findAll())).hasSize(2);
    }

    @Test
    @DisplayName("La migración asigna el hotel por defecto a filas antiguas (tenant_id vacío, como deja MySQL al agregar la columna)")
    void migracionAsignaHotelPorDefecto() throws Exception {
        jdbc.update("INSERT INTO cuartos (tenant_id, numero) VALUES ('', '999')");

        new com.staysync.tenant.TenantMigracion(
                repoEmf(), jdbc, "staysync-demo", List.of()).run(null);

        assertThat(jdbc.queryForObject("SELECT tenant_id FROM cuartos WHERE numero = '999'", String.class))
                .isEqualTo("staysync-demo");
    }

    @Autowired EntityManagerFactory emf;

    private EntityManagerFactory repoEmf() {
        return emf;
    }
}
