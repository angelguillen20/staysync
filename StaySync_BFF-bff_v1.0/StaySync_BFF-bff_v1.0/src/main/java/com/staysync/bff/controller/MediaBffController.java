package com.staysync.bff.controller;

import com.staysync.bff.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sube fotos (logo del hotel, habitaciones, servicios) a media/ del bucket del frontend, que
 * API Gateway ya publica en /{proxy+}: la URL devuelta ("/media/...") sirve en el mismo dominio
 * del sitio. Cada hotel escribe solo en su carpeta.
 */
@RestController
@RequestMapping("/bff/media")
@RequiredArgsConstructor
@Tag(name = "BFF Media", description = "Fotos de hoteles, habitaciones y servicios")
public class MediaBffController {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> CARPETAS = Set.of("hoteles", "habitaciones", "servicios");

    private final S3Client s3;
    private final JwtService jwtService;

    @Value("${staysync.media.bucket:}")
    private String bucket;

    @Operation(summary = "Subir una foto (JPG, PNG o WebP, hasta 5 MB)", description = "Devuelve { url: \"/media/...\" }")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> subir(@RequestHeader("Authorization") String authHeader,
                                     @RequestParam("archivo") MultipartFile archivo,
                                     @RequestParam("carpeta") String carpeta) throws IOException {
        if (bucket == null || bucket.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La subida de fotos no está configurada; pega un enlace https a la imagen.");
        }
        if (!CARPETAS.contains(carpeta)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Carpeta inválida: " + carpeta);
        }
        if (archivo.isEmpty() || archivo.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto debe pesar hasta 5 MB.");
        }
        byte[] bytes = archivo.getBytes();
        String[] tipo = tipoImagen(bytes);
        if (tipo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se aceptan fotos JPG, PNG o WebP.");
        }
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String hotel = jwtService.extractTenantId(token);
        String clave = "media/" + hotel + "/" + carpeta + "/" + UUID.randomUUID() + "." + tipo[0];

        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket).key(clave).contentType(tipo[1])
                        // El nombre es único por subida: el navegador puede guardarla para siempre
                        .cacheControl("public, max-age=31536000, immutable")
                        .build(),
                RequestBody.fromBytes(bytes));
        return Map.of("url", "/" + clave);
    }

    /** Extensión y content-type según los primeros bytes del archivo, no según lo que declare el navegador. */
    static String[] tipoImagen(byte[] b) {
        if (empieza(b, 0, 0xFF, 0xD8, 0xFF)) return new String[] {"jpg", "image/jpeg"};
        if (empieza(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return new String[] {"png", "image/png"};
        if (empieza(b, 0, 'R', 'I', 'F', 'F') && empieza(b, 8, 'W', 'E', 'B', 'P')) return new String[] {"webp", "image/webp"};
        return null;
    }

    private static boolean empieza(byte[] b, int desde, int... firma) {
        if (b.length < desde + firma.length) return false;
        return Arrays.equals(Arrays.copyOfRange(b, desde, desde + firma.length),
                toBytes(firma));
    }

    private static byte[] toBytes(int[] valores) {
        byte[] r = new byte[valores.length];
        for (int i = 0; i < valores.length; i++) r[i] = (byte) valores[i];
        return r;
    }
}
