package com.staysync.ota.dto.request;

import lombok.Data;

@Data
public class SincronizarEstadoRequest {
    private Long habitacionId;
    private String estadoAnterior;
    private String estadoNuevo;
}
