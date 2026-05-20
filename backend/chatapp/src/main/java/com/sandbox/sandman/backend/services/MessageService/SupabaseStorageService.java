package com.sandbox.sandman.backend.services.MessageService;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupabaseStorageService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.supabase.url:}")
    private String supabaseUrl;

    @Value("${app.supabase.service-role-key:}")
    private String serviceRoleKey;

    @Value("${app.supabase.storage.bucket-name:images}")
    private String bucketName;

    public UploadResult uploadGeneratedImage(byte[] bytes, String mimeType) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Generated image is empty");
        }
        if (isBlank(supabaseUrl) || isBlank(serviceRoleKey)) {
            throw new IllegalStateException("Supabase Storage is not configured");
        }

        String objectPath = buildObjectPath(mimeType);
        String uploadUrl = trimRightSlash(supabaseUrl)
                + "/storage/v1/object/" + bucketName + "/" + objectPath;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serviceRoleKey.trim());
        headers.set("apikey", serviceRoleKey.trim());
        headers.set("x-upsert", "false");
        headers.setContentType(MediaType.parseMediaType(mimeType));

        restTemplate.exchange(uploadUrl, HttpMethod.POST, new HttpEntity<>(bytes, headers), String.class);

        String publicUrl = trimRightSlash(supabaseUrl)
                + "/storage/v1/object/public/" + bucketName + "/" + objectPath;
        return new UploadResult(objectPath, publicUrl);
    }

    private String buildObjectPath(String mimeType) {
        String extension = MediaType.IMAGE_JPEG_VALUE.equals(mimeType) ? "jpg" : "png";
        LocalDate today = LocalDate.now();
        return "chatapp/generated/%d/%02d/%02d/%s.%s".formatted(
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                UUID.randomUUID(),
                extension
        );
    }

    private String trimRightSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record UploadResult(String objectPath, String publicUrl) {}
}
