package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.ProfileDto;
import com.sandbox.sandman.backend.model.dto.ProfileUpsertRequest;
import com.sandbox.sandman.backend.model.entity.UserJobProfile;
import com.sandbox.sandman.backend.repositories.UserJobProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileServiceTest {

    @Test
    void upsertNormalizesProfileFieldsUsedForMatchingAndRouting() {
        UserJobProfileRepository repository = mock(UserJobProfileRepository.class);
        when(repository.findByUserId(42L)).thenReturn(Optional.empty());
        when(repository.save(any(UserJobProfile.class))).thenAnswer(invocation -> {
            UserJobProfile profile = invocation.getArgument(0);
            profile.setId(7L);
            return profile;
        });
        ProfileService service = new ProfileService(repository, new JobjabMapper());

        ProfileDto result = service.upsert(42L, new ProfileUpsertRequest(
                "  React engineer  ",
                List.of(" Frontend Developer ", "", "Frontend Developer"),
                List.of(" React ", "TypeScript"),
                List.of(" 2 years React "),
                List.of(" Bangkok "),
                List.of(" Full-time "),
                90_000,
                50_000,
                "  Bangkok, Thailand  ",
                null,
                null,
                999,
                " transit ",
                null
        ));

        assertThat(result.headline()).isEqualTo("React engineer");
        assertThat(result.desiredTitles()).containsExactly("Frontend Developer");
        assertThat(result.salaryMin()).isEqualTo(50_000);
        assertThat(result.salaryMax()).isEqualTo(90_000);
        assertThat(result.homeLocationLabel()).isEqualTo("Bangkok, Thailand");
        assertThat(result.maxCommuteMinutes()).isEqualTo(240);
        assertThat(result.travelMode()).isEqualTo("TRANSIT");
        assertThat(result.weeklyDigestEnabled()).isTrue();
    }

    @Test
    void invalidTravelModeFallsBackToDriveAndNegativeNumbersClamp() {
        UserJobProfileRepository repository = mock(UserJobProfileRepository.class);
        when(repository.findByUserId(42L)).thenReturn(Optional.empty());
        when(repository.save(any(UserJobProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProfileService service = new ProfileService(repository, new JobjabMapper());

        ProfileDto result = service.upsert(42L, new ProfileUpsertRequest(
                " ",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                -1,
                -5,
                " ",
                null,
                null,
                -30,
                "teleport",
                false
        ));

        assertThat(result.headline()).isNull();
        assertThat(result.salaryMin()).isEqualTo(0);
        assertThat(result.salaryMax()).isEqualTo(0);
        assertThat(result.homeLocationLabel()).isNull();
        assertThat(result.maxCommuteMinutes()).isZero();
        assertThat(result.travelMode()).isEqualTo("DRIVE");
        assertThat(result.weeklyDigestEnabled()).isFalse();
    }
}
