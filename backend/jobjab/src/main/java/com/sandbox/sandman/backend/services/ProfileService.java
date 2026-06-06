package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.ProfileDto;
import com.sandbox.sandman.backend.model.dto.ProfileUpsertRequest;
import com.sandbox.sandman.backend.model.entity.UserJobProfile;
import com.sandbox.sandman.backend.repositories.UserJobProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final UserJobProfileRepository profileRepository;
    private final JobjabMapper mapper;

    @Transactional(readOnly = true)
    public ProfileDto get(Long userId) {
        return profileRepository.findByUserId(userId).map(mapper::toDto).orElse(null);
    }

    @Transactional
    public ProfileDto upsert(Long userId, ProfileUpsertRequest req) {
        UserJobProfile profile = profileRepository.findByUserId(userId).orElseGet(() -> {
            UserJobProfile p = new UserJobProfile();
            p.setUserId(userId);
            return p;
        });

        profile.setHeadline(trimToNull(req.headline()));
        profile.setDesiredTitles(list(req.desiredTitles()));
        profile.setSkills(list(req.skills()));
        profile.setExperiences(list(req.experiences()));
        profile.setPreferredLocations(list(req.preferredLocations()));
        profile.setEmploymentTypes(list(req.employmentTypes()));
        profile.setSalaryMin(salaryMin(req.salaryMin(), req.salaryMax()));
        profile.setSalaryMax(salaryMax(req.salaryMin(), req.salaryMax()));
        profile.setHomeLocationLabel(trimToNull(req.homeLocationLabel()));
        profile.setHomeLatitude(req.homeLatitude());
        profile.setHomeLongitude(req.homeLongitude());
        profile.setMaxCommuteMinutes(commuteMinutes(req.maxCommuteMinutes()));
        profile.setTravelMode(travelMode(req.travelMode()));
        profile.setWeeklyDigestEnabled(req.weeklyDigestEnabled() == null || req.weeklyDigestEnabled());
        return mapper.toDto(profileRepository.save(profile));
    }

    private List<String> list(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String travelMode(String value) {
        if (value == null) return "DRIVE";
        return switch (value.trim().toUpperCase()) {
            case "TRANSIT", "WALK", "TWO_WHEELER" -> value.trim().toUpperCase();
            default -> "DRIVE";
        };
    }

    private Integer commuteMinutes(Integer value) {
        if (value == null) return null;
        return Math.min(240, Math.max(0, value));
    }

    private Integer salaryMin(Integer min, Integer max) {
        if (min == null) return null;
        int normalizedMin = Math.max(0, min);
        if (max == null) return normalizedMin;
        return Math.min(normalizedMin, Math.max(0, max));
    }

    private Integer salaryMax(Integer min, Integer max) {
        if (max == null) return null;
        int normalizedMax = Math.max(0, max);
        if (min == null) return normalizedMax;
        return Math.max(normalizedMax, Math.max(0, min));
    }
}
