package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.services.BlobStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("${app.api.prefix.b-post}")
@Slf4j
public class ReportController {
    @Autowired
    private BlobStorageService blobStorageService;

    @PostMapping("/blog/upload-image")
    public ResponseEntity<ByteArrayResource> uploadImage(@RequestParam("imageFile") MultipartFile file) {
        log.info("Start uploading image file");
        return blobStorageService.uploadImage(file);
    }
}
