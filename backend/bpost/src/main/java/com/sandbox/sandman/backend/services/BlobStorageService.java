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

    public ResponseEntity<ByteArrayResource> uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Your uploaded file is null or empty");
        }
        try {
            String uniqueFilename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            String bucket = appConfig.getImageContainerName();
            String uploadUrl = appConfig.getSupabaseUrl()
                    + "/storage/v1/object/" + bucket + "/" + uniqueFilename;

            String serviceKey = appConfig.getServiceRoleKey().trim();
            // DEBUG — remove after fixing
            log.info("[DEBUG] uploadUrl = {}", uploadUrl);
            log.info("[DEBUG] serviceKey length = {}", serviceKey.length());
            log.info("[DEBUG] serviceKey starts = {}", serviceKey.substring(0, Math.min(20, serviceKey.length())));
            log.info("[DEBUG] serviceKey ends   = {}", serviceKey.substring(Math.max(0, serviceKey.length() - 10)));
            log.info("[DEBUG] Authorization header = Bearer {}", serviceKey.substring(0, Math.min(20, serviceKey.length())) + "...");

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + serviceKey);
            headers.set("apikey", serviceKey);
            headers.setContentType(MediaType.parseMediaType(file.getContentType()));

            HttpEntity<byte[]> requestEntity = new HttpEntity<>(file.getBytes(), headers);
            restTemplate.exchange(uploadUrl, HttpMethod.POST, requestEntity, String.class);

            // Return file bytes back to caller (matches previous Azure behaviour)
            ByteArrayResource resource = new ByteArrayResource(file.getBytes());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getOriginalFilename() + "\"")
                    .contentType(MediaType.parseMediaType(file.getContentType()))
                    .contentLength(file.getSize())
                    .body(resource);
        } catch (Exception e) {
            throw new RuntimeException("Error image upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the public URL of an uploaded file.
     */
    public String getPublicUrl(String filename) {
        String bucket = appConfig.getImageContainerName();
        return appConfig.getSupabaseUrl() + "/storage/v1/object/public/" + bucket + "/" + filename;
    }
}