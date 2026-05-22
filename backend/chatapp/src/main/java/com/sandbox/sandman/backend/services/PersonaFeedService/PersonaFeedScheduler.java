package com.sandbox.sandman.backend.services.PersonaFeedService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PersonaFeedScheduler {

    private static final int CLAIM_LIMIT = 5;

    private final PersonaFeedService personaFeedService;

    @Scheduled(
            fixedDelayString = "${app.persona-feed.poll-delay-ms:60000}",
            initialDelayString = "${app.persona-feed.initial-delay-ms:60000}"
    )
    public void publishDuePosts() {
        for (Long personaId : personaFeedService.claimDuePersonaIds(CLAIM_LIMIT)) {
            try {
                personaFeedService.publishClaimedPersona(personaId);
            } catch (Exception e) {
                log.warn("PersonaFeed worker failed after claiming persona {}", personaId, e);
            }
        }
    }
}
