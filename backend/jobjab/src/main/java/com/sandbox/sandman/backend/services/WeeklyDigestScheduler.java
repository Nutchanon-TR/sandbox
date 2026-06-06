package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.entity.UserJobProfile;
import com.sandbox.sandman.backend.repositories.UserJobProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WeeklyDigestScheduler {
    private final UserJobProfileRepository profileRepository;
    private final WeeklyDigestService weeklyDigestService;

    @Value("${app.jobjab.digest.enabled:false}")
    private boolean enabled;

    @Scheduled(cron = "${app.jobjab.digest.cron:0 0 8 ? * MON}", zone = "Asia/Bangkok")
    public void generateWeeklyDigests() {
        if (!enabled) {
            return;
        }
        int generated = 0;
        for (UserJobProfile profile : profileRepository.findByWeeklyDigestEnabledTrue()) {
            try {
                weeklyDigestService.generate(profile.getUserId());
                generated++;
            } catch (RuntimeException ex) {
                log.warn("Unable to generate JOBJAB weekly digest for user {}", profile.getUserId(), ex);
            }
        }
        log.info("Generated {} JOBJAB weekly digests", generated);
    }
}
