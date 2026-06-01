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

        profile.setHeadline(req.headline());
        profile.setDesiredTitles(list(req.desiredTitles()));
        profile.setSkills(list(req.skills()));
        profile.setExperiences(list(req.experiences()));
        profile.setPreferredLocations(list(req.preferredLocations()));
        profile.setEmploymentTypes(list(req.employmentTypes()));
        profile.setSalaryMin(req.salaryMin());
        profile.setSalaryMax(req.salaryMax());
        profile.setHomeLocationLabel(req.homeLocationLabel());
        profile.setHomeLatitude(req.homeLatitude());
        profile.setHomeLongitude(req.homeLongitude());
        profile.setMaxCommuteMinutes(req.maxCommuteMinutes());
        profile.setTravelMode(req.travelMode() == null || req.travelMode().isBlank() ? "DRIVE" : req.travelMode());
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
}
