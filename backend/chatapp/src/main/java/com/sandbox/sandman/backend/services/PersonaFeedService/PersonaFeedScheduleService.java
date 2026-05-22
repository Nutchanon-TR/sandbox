package com.sandbox.sandman.backend.services.PersonaFeedService;

import com.sandbox.sandman.backend.model.entity.MessageEntity.AiContext;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PersonaFeedScheduleService {

    public static final int DEFAULT_MIN_INTERVAL_HOURS = 8;
    public static final int DEFAULT_MAX_INTERVAL_HOURS = 24;
    public static final String DEFAULT_TIMEZONE = "Asia/Bangkok";

    public ZonedDateTime nextPostAt(AiContext persona, ZonedDateTime anchor) {
        int minHours = safeMinHours(persona.getPersonaFeedMinIntervalHours());
        int maxHours = safeMaxHours(persona.getPersonaFeedMaxIntervalHours(), minHours);
        long minSeconds = Duration.ofHours(minHours).toSeconds();
        long maxSeconds = Duration.ofHours(maxHours).toSeconds();
        long offsetSeconds = ThreadLocalRandom.current().nextLong(minSeconds, maxSeconds + 1);
        ZonedDateTime candidate = anchor.plusSeconds(offsetSeconds);

        LocalTime windowStart = persona.getPersonaFeedWindowStart();
        LocalTime windowEnd = persona.getPersonaFeedWindowEnd();
        if (windowStart == null || windowEnd == null || windowStart.equals(windowEnd)) {
            return candidate;
        }

        ZoneId zone = safeZone(persona.getPersonaFeedTimezone());
        ZonedDateTime localCandidate = candidate.withZoneSameInstant(zone);
        if (insideWindow(localCandidate.toLocalTime(), windowStart, windowEnd)) {
            return localCandidate;
        }

        return randomInNextWindow(localCandidate, windowStart, windowEnd);
    }

    private ZonedDateTime randomInNextWindow(ZonedDateTime candidate, LocalTime start, LocalTime end) {
        LocalDate day = candidate.toLocalDate();
        ZonedDateTime windowStart;
        ZonedDateTime windowEnd;

        if (start.isBefore(end)) {
            if (!candidate.toLocalTime().isBefore(end)) {
                day = day.plusDays(1);
            }
            windowStart = ZonedDateTime.of(day, start, candidate.getZone());
            windowEnd = ZonedDateTime.of(day, end, candidate.getZone());
        } else {
            windowStart = ZonedDateTime.of(day, start, candidate.getZone());
            windowEnd = ZonedDateTime.of(day.plusDays(1), end, candidate.getZone());
        }

        long spanSeconds = Math.max(Duration.between(windowStart, windowEnd).toSeconds(), 1);
        long offsetSeconds = ThreadLocalRandom.current().nextLong(spanSeconds);
        return windowStart.plusSeconds(offsetSeconds);
    }

    private boolean insideWindow(LocalTime value, LocalTime start, LocalTime end) {
        if (start.isBefore(end)) {
            return !value.isBefore(start) && value.isBefore(end);
        }
        return !value.isBefore(start) || value.isBefore(end);
    }

    private ZoneId safeZone(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone.trim());
        } catch (DateTimeException ignored) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }

    private int safeMinHours(Integer value) {
        return value == null || value <= 0 ? DEFAULT_MIN_INTERVAL_HOURS : value;
    }

    private int safeMaxHours(Integer value, int minHours) {
        int maxHours = value == null ? DEFAULT_MAX_INTERVAL_HOURS : value;
        return Math.max(maxHours, minHours);
    }
}
