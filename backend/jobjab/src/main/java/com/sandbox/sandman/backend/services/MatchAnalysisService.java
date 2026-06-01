package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.JobMatchDto;
import com.sandbox.sandman.backend.model.entity.Job;
import com.sandbox.sandman.backend.model.entity.JobMatch;
import com.sandbox.sandman.backend.model.entity.UserJobProfile;
import com.sandbox.sandman.backend.repositories.JobMatchRepository;
import com.sandbox.sandman.backend.repositories.UserJobProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchAnalysisService {
    private static final List<String> KNOWN_SKILLS = List.of(
            "JavaScript", "TypeScript", "React", "Next.js", "Vue", "Angular", "Node.js",
            "Java", "Spring Boot", "SQL", "PostgreSQL", "MySQL", "Python", "Django",
            "Docker", "Kubernetes", "AWS", "GCP", "Azure", "Git", "REST API", "GraphQL",
            "Tailwind", "Ant Design", "Supabase", "Figma", "Agile", "Scrum", "CI/CD",
            "Testing", "Jest", "Cypress", "Playwright", "English", "Thai", "UX", "UI"
    );

    private final JobService jobService;
    private final UserJobProfileRepository profileRepository;
    private final JobMatchRepository jobMatchRepository;
    private final JobjabMapper mapper;
    private final JobjabAiClient jobjabAiClient;

    @Transactional
    public JobMatchDto analyze(Long userId, Long jobId) {
        Job job = jobService.find(jobId);
        UserJobProfile profile = profileRepository.findByUserId(userId).orElse(null);

        List<String> profileSkills = profile == null ? List.of() : expandProfileSkills(profile);
        List<String> jobSkills = requiredSkills(job);
        Set<String> normalizedProfileSkills = profileSkills.stream().map(this::norm).collect(Collectors.toSet());

        List<String> matched = jobSkills.stream()
                .filter(skill -> normalizedProfileSkills.contains(norm(skill)))
                .distinct()
                .toList();
        List<String> missing = jobSkills.stream()
                .filter(skill -> !normalizedProfileSkills.contains(norm(skill)))
                .distinct()
                .toList();
        List<String> redFlags = redFlags(job, profile);
        int score = score(jobSkills.size(), matched.size(), missing.size(), redFlags.size(), job, profile);
        String summary = summary(score, matched, missing, redFlags);

        Optional<AiMatchSuggestion> aiSuggestion = jobjabAiClient.analyze(job, profile);
        if (aiSuggestion.isPresent()) {
            AiMatchSuggestion ai = aiSuggestion.get();
            score = ai.matchScore() == null ? score : ai.matchScore();
            matched = ai.matchedSkills() == null || ai.matchedSkills().isEmpty() ? matched : ai.matchedSkills();
            missing = ai.missingSkills() == null || ai.missingSkills().isEmpty() ? missing : ai.missingSkills();
            redFlags = ai.redFlags() == null ? redFlags : ai.redFlags();
            summary = ai.summary() == null || ai.summary().isBlank() ? summary : ai.summary();
        }

        JobMatch match = jobMatchRepository.findByUserIdAndJobId(userId, jobId).orElseGet(JobMatch::new);
        match.setUserId(userId);
        match.setJobId(jobId);
        match.setProfileId(profile == null ? null : profile.getId());
        match.setMatchScore(score);
        match.setMatchedSkills(matched);
        match.setMissingSkills(missing);
        match.setRedFlags(redFlags);
        match.setAiSummary(summary);
        return mapper.toDto(jobMatchRepository.save(match));
    }

    private int score(int totalSkills, int matched, int missing, int redFlags, Job job, UserJobProfile profile) {
        int base = totalSkills == 0 ? 55 : 55 + matched * 8 - missing * 7;
        if (profile != null && profile.getPreferredLocations() != null && job.getLocationText() != null) {
            boolean preferred = profile.getPreferredLocations().stream()
                    .filter(location -> location != null && !location.isBlank())
                    .anyMatch(location -> job.getLocationText().toLowerCase(Locale.ROOT).contains(location.toLowerCase(Locale.ROOT)));
            if (preferred) base += 5;
        }
        return Math.max(15, Math.min(95, base - redFlags * 6));
    }

    private String summary(int score, List<String> matched, List<String> missing, List<String> redFlags) {
        StringBuilder sb = new StringBuilder("Match score ").append(score).append("/100.");
        if (!matched.isEmpty()) sb.append(" Strong overlap: ").append(String.join(", ", matched)).append(".");
        if (!missing.isEmpty()) sb.append(" This job asks for ").append(String.join(", ", missing)).append(", which is not in your profile yet.");
        if (!redFlags.isEmpty()) sb.append(" Red flags: ").append(String.join(", ", redFlags)).append(".");
        if (matched.isEmpty() && missing.isEmpty()) sb.append(" Add more JD text or profile skills for a sharper analysis.");
        return sb.toString();
    }

    private List<String> redFlags(Job job, UserJobProfile profile) {
        String text = ((job.getTitle() == null ? "" : job.getTitle()) + " " + (job.getDescription() == null ? "" : job.getDescription()))
                .toLowerCase(Locale.ROOT);
        Map<String, String> patterns = new LinkedHashMap<>();
        patterns.put("unpaid", "Unpaid or unclear compensation");
        patterns.put("commission only", "Commission-only pay");
        patterns.put("competitive salary", "Salary is vague");
        patterns.put("urgent", "Urgent hiring pressure");
        patterns.put("fast-paced", "Potential high-pressure environment");
        patterns.put("night shift", "Night shift requirement");
        patterns.put("weekend", "Weekend work mentioned");
        patterns.put("overtime", "Overtime mentioned");
        patterns.put("own laptop", "Requires own equipment");
        patterns.put("contract only", "Contract-only role");
        List<String> flags = new ArrayList<>(patterns.entrySet().stream()
                .filter(entry -> text.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .distinct()
                .toList());
        if (job.getSalaryMin() == null && job.getSalaryMax() == null) {
            flags.add("Salary not listed");
        }
        if (profile != null && profile.getSalaryMin() != null && job.getSalaryMax() != null && job.getSalaryMax() < profile.getSalaryMin()) {
            flags.add("Below salary expectation");
        }
        if (profile != null && profile.getEmploymentTypes() != null && !profile.getEmploymentTypes().isEmpty() && job.getEmploymentType() != null) {
            boolean preferred = profile.getEmploymentTypes().stream()
                    .filter(type -> type != null && !type.isBlank())
                    .anyMatch(type -> job.getEmploymentType().toLowerCase(Locale.ROOT).contains(type.toLowerCase(Locale.ROOT)));
            if (!preferred) flags.add("Employment type differs from preference");
        }
        return flags.stream().distinct().toList();
    }

    private List<String> requiredSkills(Job job) {
        LinkedHashSet<String> skills = new LinkedHashSet<>(safe(job.getSkills()));
        String text = ((job.getTitle() == null ? "" : job.getTitle()) + " " + (job.getDescription() == null ? "" : job.getDescription()))
                .toLowerCase(Locale.ROOT);
        KNOWN_SKILLS.stream()
                .filter(skill -> text.contains(skill.toLowerCase(Locale.ROOT)))
                .forEach(skills::add);
        return skills.stream().toList();
    }

    private List<String> expandProfileSkills(UserJobProfile profile) {
        LinkedHashSet<String> skills = new LinkedHashSet<>(safe(profile.getSkills()));
        String text = (safe(profile.getExperiences()).toString() + " " + (profile.getHeadline() == null ? "" : profile.getHeadline()))
                .toLowerCase(Locale.ROOT);
        KNOWN_SKILLS.stream()
                .filter(skill -> text.contains(skill.toLowerCase(Locale.ROOT)))
                .forEach(skills::add);
        return skills.stream().toList();
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String norm(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
