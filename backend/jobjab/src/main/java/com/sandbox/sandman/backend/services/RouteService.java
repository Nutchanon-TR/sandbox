package com.sandbox.sandman.backend.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sandbox.sandman.backend.model.dto.RouteDto;
import com.sandbox.sandman.backend.model.dto.RouteRequest;
import com.sandbox.sandman.backend.model.entity.Job;
import com.sandbox.sandman.backend.model.entity.RouteCache;
import com.sandbox.sandman.backend.model.entity.UserJobProfile;
import com.sandbox.sandman.backend.repositories.RouteCacheRepository;
import com.sandbox.sandman.backend.repositories.UserJobProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RouteService {
    private final JobService jobService;
    private final UserJobProfileRepository profileRepository;
    private final RouteCacheRepository routeCacheRepository;
    private final JobjabMapper mapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${app.google.maps.api-key:}")
    private String googleMapsApiKey;

    @Transactional
    public RouteDto compute(Long userId, Long jobId, RouteRequest req) {
        Job job = jobService.find(jobId);
        UserJobProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Create a JOBJAB profile before computing routes"));
        String travelMode = req.travelMode() == null || req.travelMode().isBlank()
                ? defaultMode(profile.getTravelMode())
                : defaultMode(req.travelMode());
        String cacheKey = cacheKey(userId, jobId, travelMode, profile, job);
        RouteCache cached = routeCacheRepository.findByCacheKey(cacheKey).orElse(null);
        if (cached != null && (cached.getExpiresAt() == null || cached.getExpiresAt().isAfter(ZonedDateTime.now()))) {
            return mapper.toDto(cached);
        }

        RouteCache route = canUseGoogle(profile, job)
                ? googleRoute(userId, jobId, travelMode, cacheKey, profile, job)
                : estimateRoute(userId, jobId, travelMode, cacheKey, profile, job);
        return mapper.toDto(routeCacheRepository.save(cached == null ? route : refreshCachedRoute(cached, route)));
    }

    private RouteCache refreshCachedRoute(RouteCache cached, RouteCache route) {
        cached.setUserId(route.getUserId());
        cached.setJobId(route.getJobId());
        cached.setCacheKey(route.getCacheKey());
        cached.setTravelMode(route.getTravelMode());
        cached.setOriginLabel(route.getOriginLabel());
        cached.setDestinationLabel(route.getDestinationLabel());
        cached.setDistanceMeters(route.getDistanceMeters());
        cached.setDurationSeconds(route.getDurationSeconds());
        cached.setProvider(route.getProvider());
        cached.setRawPayload(route.getRawPayload());
        cached.setExpiresAt(route.getExpiresAt());
        return cached;
    }

    private RouteCache googleRoute(Long userId, Long jobId, String travelMode, String cacheKey, UserJobProfile profile, Job job) {
        try {
            Map<String, Object> body = Map.of(
                    "origin", waypoint(profile.getHomeLatitude(), profile.getHomeLongitude(), profile.getHomeLocationLabel()),
                    "destination", waypoint(job.getLocationLatitude(), job.getLocationLongitude(), job.getLocationText()),
                    "travelMode", googleTravelMode(travelMode)
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://routes.googleapis.com/directions/v2:computeRoutes"))
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", googleMapsApiKey)
                    .header("X-Goog-FieldMask", "routes.duration,routes.distanceMeters")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Map<String, Object> json = objectMapper.readValue(response.body(), new TypeReference<>() {});
                List<Map<String, Object>> routes = (List<Map<String, Object>>) json.getOrDefault("routes", List.of());
                if (!routes.isEmpty()) {
                    Map<String, Object> first = routes.get(0);
                    RouteCache route = baseRoute(userId, jobId, travelMode, cacheKey, profile, job);
                    route.setProvider("GOOGLE_MAPS");
                    route.setDistanceMeters(intValue(first.get("distanceMeters")));
                    route.setDurationSeconds(durationSeconds(first.get("duration")));
                    route.setRawPayload(json);
                    route.setExpiresAt(ZonedDateTime.now().plusDays(7));
                    return route;
                }
            }
        } catch (Exception ignored) {
            return estimateRoute(userId, jobId, travelMode, cacheKey, profile, job);
        }
        return estimateRoute(userId, jobId, travelMode, cacheKey, profile, job);
    }

    private RouteCache estimateRoute(Long userId, Long jobId, String travelMode, String cacheKey, UserJobProfile profile, Job job) {
        RouteCache route = baseRoute(userId, jobId, travelMode, cacheKey, profile, job);
        route.setProvider("ESTIMATE");
        if (hasCoords(profile.getHomeLatitude(), profile.getHomeLongitude()) && hasCoords(job.getLocationLatitude(), job.getLocationLongitude())) {
            double km = haversineKm(profile.getHomeLatitude(), profile.getHomeLongitude(), job.getLocationLatitude(), job.getLocationLongitude());
            double roadFactor = "WALK".equals(travelMode) ? 1.15 : 1.35;
            int meters = (int) Math.round(km * roadFactor * 1000);
            route.setDistanceMeters(meters);
            route.setDurationSeconds((int) Math.round((meters / 1000.0) / speedKmh(travelMode) * 3600));
            route.setRawPayload(Map.of("method", "haversine_estimate", "roadFactor", roadFactor));
        } else {
            route.setRawPayload(Map.of("method", "missing_coordinates"));
        }
        route.setExpiresAt(ZonedDateTime.now().plusDays(3));
        return route;
    }

    private RouteCache baseRoute(Long userId, Long jobId, String travelMode, String cacheKey, UserJobProfile profile, Job job) {
        RouteCache route = new RouteCache();
        route.setUserId(userId);
        route.setJobId(jobId);
        route.setTravelMode(travelMode);
        route.setCacheKey(cacheKey);
        route.setOriginLabel(profile.getHomeLocationLabel());
        route.setDestinationLabel(job.getLocationText());
        return route;
    }

    private boolean canUseGoogle(UserJobProfile profile, Job job) {
        return googleMapsApiKey != null && !googleMapsApiKey.isBlank()
                && hasWaypoint(profile.getHomeLatitude(), profile.getHomeLongitude(), profile.getHomeLocationLabel())
                && hasWaypoint(job.getLocationLatitude(), job.getLocationLongitude(), job.getLocationText());
    }

    private Map<String, Object> waypoint(Double lat, Double lng, String label) {
        if (hasCoords(lat, lng)) {
            return Map.of("location", Map.of("latLng", Map.of("latitude", lat, "longitude", lng)));
        }
        return Map.of("address", label.trim());
    }

    private String googleTravelMode(String mode) {
        return switch (mode) {
            case "TRANSIT" -> "TRANSIT";
            case "WALK" -> "WALK";
            case "TWO_WHEELER" -> "TWO_WHEELER";
            default -> "DRIVE";
        };
    }

    private String defaultMode(String mode) {
        if (mode == null) return "DRIVE";
        return switch (mode.trim().toUpperCase()) {
            case "TRANSIT", "WALK", "TWO_WHEELER" -> mode.trim().toUpperCase();
            default -> "DRIVE";
        };
    }

    private int speedKmh(String mode) {
        return switch (mode) {
            case "WALK" -> 5;
            case "TRANSIT" -> 25;
            case "TWO_WHEELER" -> 30;
            default -> 35;
        };
    }

    private boolean hasCoords(Double lat, Double lng) {
        return lat != null && lng != null;
    }

    private boolean hasWaypoint(Double lat, Double lng, String label) {
        return hasCoords(lat, lng) || (label != null && !label.isBlank());
    }

    private int intValue(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return 0;
        return Integer.parseInt(String.valueOf(value));
    }

    private int durationSeconds(Object value) {
        if (value == null) return 0;
        String text = String.valueOf(value);
        if (text.endsWith("s")) text = text.substring(0, text.length() - 1);
        return (int) Math.round(Double.parseDouble(text));
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double radius = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return radius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String cacheKey(Long userId, Long jobId, String travelMode, UserJobProfile profile, Job job) {
        try {
            String raw = userId + ":" + jobId + ":" + travelMode + ":" + profile.getHomeLatitude() + ":" + profile.getHomeLongitude()
                    + ":" + profile.getHomeLocationLabel() + ":" + job.getLocationLatitude() + ":" + job.getLocationLongitude()
                    + ":" + job.getLocationText();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create route cache key", ex);
        }
    }
}
