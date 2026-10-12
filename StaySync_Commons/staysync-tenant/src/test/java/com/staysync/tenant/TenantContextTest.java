package com.staysync.tenant;

import org.junit.jupiter.api.*;
import org.slf4j.MDC;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TenantContext")
class TenantContextTest {

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("set() guarda el hotel y lo pone en el MDC")
    void setGuardaYPoneEnMdc() {
        TenantContext.set("hotel-andes");

        assertThat(TenantContext.get()).contains("hotel-andes");
        assertThat(TenantContext.require()).isEqualTo("hotel-andes");
        assertThat(MDC.get(TenantHeaders.MDC_KEY)).isEqualTo("hotel-andes");
    }

    @Test
    @DisplayName("set() rechaza formatos inválidos")
    void setRechazaInvalidos() {
        assertThatThrownBy(() -> TenantContext.set("Hotel Andes")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantContext.set("x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantContext.set(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(TenantContext.get()).isEmpty();
    }

    @Test
    @DisplayName("require() sin hotel lanza TenantNotResolvedException")
    void requireSinHotel() {
        assertThatThrownBy(TenantContext::require).isInstanceOf(TenantNotResolvedException.class);
    }

    @Test
    @DisplayName("clear() borra el hotel y el MDC")
    void clearBorraTodo() {
        TenantContext.set("hotel-andes");
        TenantContext.clear();

        assertThat(TenantContext.get()).isEmpty();
        assertThat(MDC.get(TenantHeaders.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("wrap() propaga el hotel a otro hilo sin dejarlo pegado en el pool")
    void wrapPropagaAOtroHilo() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            TenantContext.set("hotel-andes");
            String visto = CompletableFuture.supplyAsync(
                    TenantContext.wrap(() -> TenantContext.get().orElse("ninguno")), pool).get();
            TenantContext.clear();
            String despues = CompletableFuture.supplyAsync(
                    () -> TenantContext.get().orElse("ninguno"), pool).get();

            assertThat(visto).isEqualTo("hotel-andes");
            assertThat(despues).isEqualTo("ninguno");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("ejecutarCon() restaura el hotel anterior")
    void ejecutarConRestaura() {
        TenantContext.set("staysync-demo");

        String dentro = TenantContext.ejecutarCon("hotel-andes", TenantContext::require);

        assertThat(dentro).isEqualTo("hotel-andes");
        assertThat(TenantContext.require()).isEqualTo("staysync-demo");
    }
}
