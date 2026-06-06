package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.JobMatchDto;
import com.sandbox.sandman.backend.model.entity.Job;
import com.sandbox.sandman.backend.model.entity.JobMatch;
import com.sandbox.sandman.backend.model.entity.UserJobProfile;
import com.sandbox.sandman.backend.repositories.JobMatchRepository;
import com.sandbox.sandman.backend.repositories.UserJobProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MatchAnalysisServiceTest {
    private final JobService jobService = mock(JobService.class);
    private final UserJobProfileRepository profileRepository = mock(UserJobProfileRepository.class);
    private final JobMatchRepository jobMatchRepository = mock(JobMatchRepository.class);
    private final JobjabMapper mapper = new JobjabMapper();
    private final JobjabAiClient aiClient = mock(JobjabAiClient.class);
    private final MatchAnalysisService service = new MatchAnalysisService(jobService, profileRepository, jobMatchRepository, mapper, aiClient);

    @Test
    void heuristicFindsMissingSkillsFromDescriptionAndProfileText() {
        Job job = new Job();
        job.setId(20L);
        job.setTitle("Frontend Engineer");
        job.setCompany("Acme");
        job.setDescription("Build React, TypeScript, GraphQL, and Playwright test suites. Competitive salary.");
        job.setSkills(List.of("React"));
        job.setSalaryMax(40000);
        job.setCurrency("THB");

        UserJobProfile profile = new UserJobProfile();
        profile.setId(10L);
        profile.setUserId(1L);
        profile.setHeadline("React developer");
        profile.setSkills(List.of("React"));
        profile.setExperiences(List.of("Built TypeScript dashboards"));
        profile.setSalaryMin(50000);

        when(jobService.find(20L)).thenReturn(job);
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(aiClient.analyze(job, profile)).thenReturn(Optional.empty());
        when(jobMatchRepository.findByUserIdAndJobId(1L, 20L)).thenReturn(Optional.empty());
        when(jobMatchRepository.save(any(JobMatch.class))).thenAnswer(invocation -> {
            JobMatch match = invocation.getArgument(0);
            match.setId(30L);
            return match;
        });

        JobMatchDto result = service.analyze(1L, 20L);

        assertThat(result.matchedSkills()).contains("React", "TypeScript");
        assertThat(result.missingSkills()).contains("GraphQL", "Playwright");
        assertThat(result.redFlags()).contains("Salary is vague", "Below salary expectation");
        assertThat(result.aiSummary()).contains("This job asks for GraphQL");
    }

    @Test
    void heuristicOnlyAnalysisDoesNotCallAiClient() {
        Job job = new Job();
        job.setId(21L);
        job.setTitle("Backend Engineer");
        job.setCompany("Acme");
        job.setDescription("Build Java and Spring Boot services.");
        job.setSkills(List.of("Java"));
        job.setCurrency("THB");

        UserJobProfile profile = new UserJobProfile();
        profile.setId(11L);
        profile.setUserId(1L);
        profile.setSkills(List.of("Java"));

        when(jobService.find(21L)).thenReturn(job);
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(jobMatchRepository.findByUserIdAndJobId(1L, 21L)).thenReturn(Optional.empty());
        when(jobMatchRepository.save(any(JobMatch.class))).thenAnswer(invocation -> {
            JobMatch match = invocation.getArgument(0);
            match.setId(31L);
            return match;
        });

        JobMatch result = service.analyzeHeuristicOnly(1L, 21L);

        assertThat(result.getId()).isEqualTo(31L);
        assertThat(result.getMatchedSkills()).contains("Java");
        verify(aiClient, never()).analyze(any(), any());
    }
}
