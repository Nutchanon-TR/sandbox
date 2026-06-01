package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.commonauth.CurrentUser;
import com.sandbox.sandman.backend.model.dto.JobSourceDto;
import com.sandbox.sandman.backend.services.JobSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${app.api.prefix.jobjab}/sources")
@RequiredArgsConstructor
public class SourceController {
    private final CurrentUser currentUser;
    private final JobSourceService jobSourceService;

    @GetMapping
    public ResponseEntity<List<JobSourceDto>> list() {
        currentUser.requireUserId();
        return ResponseEntity.ok(jobSourceService.listSources());
    }
}
