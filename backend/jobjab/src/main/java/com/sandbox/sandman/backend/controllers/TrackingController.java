package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.commonauth.CurrentUser;
import com.sandbox.sandman.backend.model.dto.JobTrackingDto;
import com.sandbox.sandman.backend.model.dto.TrackingUpdateRequest;
import com.sandbox.sandman.backend.services.TrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${app.api.prefix.jobjab}")
@RequiredArgsConstructor
public class TrackingController {
    private final CurrentUser currentUser;
    private final TrackingService trackingService;

    @GetMapping("/tracking")
    public ResponseEntity<List<JobTrackingDto>> list() {
        return ResponseEntity.ok(trackingService.list(currentUser.requireUserId()));
    }

    @PutMapping("/jobs/{jobId}/tracking")
    public ResponseEntity<JobTrackingDto> update(@PathVariable Long jobId, @RequestBody TrackingUpdateRequest req) {
        return ResponseEntity.ok(trackingService.update(currentUser.requireUserId(), jobId, req));
    }
}
