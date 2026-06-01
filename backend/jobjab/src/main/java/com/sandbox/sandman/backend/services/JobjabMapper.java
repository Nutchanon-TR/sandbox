package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.*;
import com.sandbox.sandman.backend.model.entity.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JobjabMapper {

    public ProfileDto toDto(UserJobProfile p) {
        if (p == null) return null;
        return new ProfileDto(
                p.getId(), p.getUserId(), p.getHeadline(),
                safe(p.getDesiredTitles()), safe(p.getSkills()), safe(p.getExperiences()),
                safe(p.getPreferredLocations()), safe(p.getEmploymentTypes()),
                p.getSalaryMin(), p.getSalaryMax(), p.getHomeLocationLabel(),
                p.getHomeLatitude(), p.getHomeLongitude(), p.getMaxCommuteMinutes(),
                p.getTravelMode(), p.isWeeklyDigestEnabled(), p.getCreatedAt(), p.getUpdatedAt()
        );
    }

    public SearchRunDto toDto(SearchRun r) {
        return new SearchRunDto(
                r.getId(), r.getUserId(), r.getProfileId(), r.getRunType(), r.getStatus(),
                r.getQuery(), r.getLocationText(), r.getRequestedLimit(), safe(r.getSourceKeys()),
                r.getStartedAt(), r.getCompletedAt(), r.getCreatedAt()
        );
    }

    public SearchRunEventDto toDto(SearchRunEvent e) {
        return new SearchRunEventDto(
                e.getId(), e.getSearchRunId(), e.getLevel(), e.getSourceKey(), e.getMessage(),
                e.getPayload(), e.getCreatedAt()
        );
    }

    public JobDto toDto(Job j, JobMatch match, JobTracking tracking) {
        return toDto(j, match, tracking, null);
    }

    public JobDto toDto(Job j, JobMatch match, JobTracking tracking, RouteCache route) {
        return new JobDto(
                j.getId(), j.getSourceId(), j.getSourceJobKey(), j.getCanonicalUrl(), j.getTitle(),
                j.getCompany(), j.getLocationText(), j.getLocationLatitude(), j.getLocationLongitude(),
                j.getSalaryMin(), j.getSalaryMax(), j.getCurrency(), j.getEmploymentType(),
                j.getWorkplaceType(), safe(j.getSkills()), j.getDescription(), j.getApplyUrl(),
                j.getPostedAt(), j.getFirstSeenAt(), j.getLastSeenAt(), toDto(match), toDto(tracking), toDto(route)
        );
    }

    public JobMatchDto toDto(JobMatch m) {
        if (m == null) return null;
        return new JobMatchDto(
                m.getId(), m.getUserId(), m.getJobId(), m.getProfileId(), m.getMatchScore(),
                safe(m.getMatchedSkills()), safe(m.getMissingSkills()), safe(m.getRedFlags()),
                m.getAiSummary(), m.getAnalyzedAt()
        );
    }

    public JobTrackingDto toDto(JobTracking t) {
        if (t == null) return null;
        return new JobTrackingDto(
                t.getId(), t.getUserId(), t.getJobId(), t.getStatus(), t.getNotes(),
                t.getAppliedAt(), t.getInterviewAt(), t.getCreatedAt(), t.getUpdatedAt()
        );
    }

    public RouteDto toDto(RouteCache route) {
        if (route == null) return null;
        return new RouteDto(
                route.getId(), route.getUserId(), route.getJobId(), route.getTravelMode(),
                route.getOriginLabel(), route.getDestinationLabel(), route.getDistanceMeters(),
                route.getDurationSeconds(), route.getProvider(), route.getCreatedAt(), route.getExpiresAt()
        );
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }
}
