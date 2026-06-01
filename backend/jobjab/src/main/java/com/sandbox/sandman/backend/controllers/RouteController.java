package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.commonauth.CurrentUser;
import com.sandbox.sandman.backend.model.dto.RouteDto;
import com.sandbox.sandman.backend.model.dto.RouteRequest;
import com.sandbox.sandman.backend.services.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${app.api.prefix.jobjab}/jobs/{jobId}/route")
@RequiredArgsConstructor
public class RouteController {
    private final CurrentUser currentUser;
    private final RouteService routeService;

    @PostMapping
    public ResponseEntity<RouteDto> compute(@PathVariable Long jobId, @RequestBody RouteRequest req) {
        return ResponseEntity.ok(routeService.compute(currentUser.requireUserId(), jobId, req));
    }
}
