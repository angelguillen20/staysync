package com.staysync.bff.controller;

import com.staysync.bff.config.SecurityConfig;
import com.staysync.bff.security.JwtAuthFilter;
import com.staysync.bff.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MediaBffController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
@TestPropertySource(properties = "staysync.media.bucket=bucket-test")
@DisplayName("Subida de fotos")
class MediaBffControllerTest {

    private static final String AUTH = "Bearer token";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @Autowired MockMvc mockMvc;
    @MockitoBean S3Client s3;
    @MockitoBean JwtService jwtService;
    @MockitoBean com.staysync.bff.client.UsuariosClient usuariosClient; // lo pide HotelPublicoFilter

    private static MockMultipartFile archivo(byte[] contenido) {
        // El nombre y el content-type declarados mienten a propósito: se valida por los bytes
        return new MockMultipartFile("archivo", "foto.png", "image/png", contenido);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Guarda la foto en la carpeta de su hotel y devuelve la URL pública")
    void subeFoto() throws Exception {
        when(jwtService.extractTenantId("token")).thenReturn("hotel-andes");

        mockMvc.perform(multipart("/bff/media").file(archivo(PNG)).param("carpeta", "habitaciones")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.matchesPattern(
                        "^/media/hotel-andes/habitaciones/[0-9a-f-]{36}\\.png$")));

        verify(s3).putObject(argThat((PutObjectRequest r) -> "bucket-test".equals(r.bucket())
                && r.key().startsWith("media/hotel-andes/habitaciones/") && "image/png".equals(r.contentType())),
                any(RequestBody.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Un PDF renombrado a .png no pasa")
    void rechazaNoImagen() throws Exception {
        mockMvc.perform(multipart("/bff/media").file(archivo("%PDF-1.7 ...".getBytes()))
                        .param("carpeta", "hoteles").header("Authorization", AUTH))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(s3);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Solo las carpetas conocidas: no se puede escribir fuera de media/<hotel>/")
    void rechazaCarpeta() throws Exception {
        mockMvc.perform(multipart("/bff/media").file(archivo(PNG)).param("carpeta", "../index.html")
                        .header("Authorization", AUTH))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(s3);
    }

    @Test
    @WithMockUser(roles = "HUESPED")
    @DisplayName("Un huésped no sube fotos")
    void huespedNoSube() throws Exception {
        mockMvc.perform(multipart("/bff/media").file(archivo(PNG)).param("carpeta", "hoteles")
                        .header("Authorization", AUTH))
                .andExpect(status().isForbidden());
        verifyNoInteractions(s3);
    }

    @Test
    @DisplayName("Reconoce JPG, PNG y WebP por sus primeros bytes")
    void reconoceFormatos() {
        org.assertj.core.api.Assertions.assertThat(MediaBffController.tipoImagen(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}))
                .containsExactly("jpg", "image/jpeg");
        org.assertj.core.api.Assertions.assertThat(MediaBffController.tipoImagen("RIFF1234WEBPVP8 ".getBytes()))
                .containsExactly("webp", "image/webp");
        org.assertj.core.api.Assertions.assertThat(MediaBffController.tipoImagen("GIF89a".getBytes())).isNull();
    }
}
