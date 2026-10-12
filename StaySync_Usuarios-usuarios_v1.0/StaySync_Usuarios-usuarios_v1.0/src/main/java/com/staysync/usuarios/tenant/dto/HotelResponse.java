package com.staysync.usuarios.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@Schema(description = "Datos públicos del hotel y su personalización visual")
public class HotelResponse {

    @Schema(example = "staysync-demo")
    private String tenantId;

    @Schema(example = "StaySync Demo")
    private String nombre;

    @Schema(example = "staysync-demo")
    private String slug;

    private String logoUrl;

    @Schema(description = "Tokens de diseño", example = "{\"primary\":\"#1F4E79\",\"secondary\":\"#F2A900\"}")
    private Map<String, String> theme;
}
