package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.config.AppConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@Service
public class BlobStorageService {

    @Autowired
    private AppConfig appConfig;

    private final RestTemplate restTemplate = new RestTemplate();

    /** Backwards-compatible upload — stores at the bucket root. */
    public ResponseEntity<ByteArrayResource> uploadImage(MultipartFile file) {
        return uploadImage(file, "");
    }

    /**
     * Uploads to Supabase Storage under {@code prefix}/uuid_filename.
     * Returns the file bytes (legacy behavior); call {@link #buildPublicUrl} with the returned
     * filename to get the URL.
     */
    public ResponseEntity<ByteArrayResource> uploadImage(MultipartFile file, String prefix) {
        UploadResult result = uploadAndReturnUrl(file, prefix);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getOriginalFilename() + "\"")
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(file.getSize())
                .body(result.body());
    }

    public UploadResult uploadAndReturnUrl(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Your uploaded file is null or empty");
        }
        try {
            String safePrefix = prefix == null ? "" : prefix.replaceAll("^/+", "").replaceAll("/+$", "");
            String objectPath = (safePrefix.isEmpty() ? "" : safePrefix + "/")
                    + UUID.randomUUID() + "_" + file.getOriginalFilename();
            String bucket = appConfig.getImageContainerName();
            String uploadUrl = appConfig.getSupabaseUrl()
                    + "/storage/v1/object/" + bucket + "/" + objectPath;

            String serviceKey = appConfig.getServiceRoleKey().trim();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + serviceKey);
            headers.set("apikey", serviceKey);
            headers.setContentType(MediaType.parseMediaType(file.getContentType()));

            HttpEntity<byte[]> requestEntity = new HttpEntity<>(file.getBytes(), headers);
            restTemplate.exchange(uploadUrl, HttpMethod.POST, requestEntity, String.class);

            String publicUrl = appConfig.getSupabaseUrl()
                    + "/storage/v1/object/public/" + bucket + "/" + objectPath;
            ByteArrayResource resource = new ByteArrayResource(file.getBytes());
            return new UploadResult(objectPath, publicUrl, resource);
        } catch (Exception e) {
            throw new RuntimeException("Image upload failed: " + e.getMessage(), e);
        }
    }

    public String buildPublicUrl(String objectPath) {
        String bucket = appConfig.getImageContainerName();
        return appConfig.getSupabaseUrl() + "/storage/v1/object/public/" + bucket + "/" + objectPath;
    }

    public String getPublicUrl(String filename) {
        return buildPublicUrl(filename);
    }

    public record UploadResult(String objectPath, String publicUrl, ByteArrayResource body) {}
}
