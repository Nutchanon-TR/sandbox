package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.adapters.JobFetchMode;
import com.sandbox.sandman.backend.adapters.JobSourceAdapter;
import com.sandbox.sandman.backend.model.dto.JobSourceDto;
import com.sandbox.sandman.backend.model.entity.JobSource;
import com.sandbox.sandman.backend.repositories.JobSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobSourceService {
    private final JobSourceRepository jobSourceRepository;
    private final List<JobSourceAdapter> adapters;

    @Transactional
    public JobSource ensureSource(JobSourceAdapter adapter) {
        return jobSourceRepository.findBySourceKey(adapter.sourceKey()).orElseGet(() -> {
            JobSource source = new JobSource();
            source.setSourceKey(adapter.sourceKey());
            source.setDisplayName(adapter.sourceKey());
            source.setFetchMode(adapter.fetchMode().name());
            return jobSourceRepository.save(source);
        });
    }

    @Transactional
    public List<JobSourceDto> listSources() {
        adapters.forEach(this::ensureSource);
        Map<String, JobSourceAdapter> registered = adapters.stream()
                .collect(Collectors.toMap(JobSourceAdapter::sourceKey, Function.identity()));
        return jobSourceRepository.findAll().stream()
                .sorted(Comparator.comparing(JobSource::getSourceKey))
                .map(source -> toDto(source, registered.get(source.getSourceKey())))
                .toList();
    }

    private JobSourceDto toDto(JobSource source, JobSourceAdapter adapter) {
        boolean registered = adapter != null;
        boolean acceptsPerSearchTargets = registered && (adapter.fetchMode() == JobFetchMode.OFFICIAL_API || adapter.fetchMode() == JobFetchMode.SITEMAP);
        boolean requiresJobUrl = registered && Set.of(JobFetchMode.USER_URL, JobFetchMode.STRUCTURED_DATA).contains(adapter.fetchMode());
        boolean configured = configured(source, adapter);
        boolean searchable = source.isEnabled() && registered && (configured || acceptsPerSearchTargets);
        return new JobSourceDto(
                source.getId(),
                source.getSourceKey(),
                source.getDisplayName(),
                source.getFetchMode(),
                source.isEnabled(),
                registered,
                configured,
                searchable,
                acceptsPerSearchTargets,
                requiresJobUrl,
                source.isRequiresPartnerApproval(),
                source.isRobotsCheckRequired(),
                source.getRateLimitPerMinute(),
                targetHint(source, adapter),
                targetExample(source, adapter),
                guidance(source, adapter, configured)
        );
    }

    private boolean configured(JobSource source, JobSourceAdapter adapter) {
        if (adapter == null) return false;
        if (adapter.fetchMode() == JobFetchMode.OFFICIAL_API || adapter.fetchMode() == JobFetchMode.SITEMAP) {
            return hasConfiguredTargets(source.getAdapterConfig());
        }
        return true;
    }

    private boolean hasConfiguredTargets(Map<String, Object> sourceConfig) {
        if (sourceConfig == null || sourceConfig.isEmpty()) return false;
        return List.of("boards", "boardTokens", "companies", "sites", "jobBoardNames", "sitemapUrls", "urls", "targets").stream()
                .map(sourceConfig::get)
                .anyMatch(value -> value instanceof List<?> list && !list.isEmpty()
                        || value instanceof String text && !text.isBlank());
    }

    private String guidance(JobSource source, JobSourceAdapter adapter, boolean configured) {
        if (!source.isEnabled()) {
            return "Source is disabled for now.";
        }
        if (adapter == null) {
            return source.isRequiresPartnerApproval()
                    ? "Needs partner/API approval before JOBJAB can fetch this platform."
                    : "No adapter registered yet.";
        }
        if (adapter.fetchMode() == JobFetchMode.OFFICIAL_API && !configured) {
            return "Ready with per-search board/company targets, or add defaults in job_sources.adapter_config.";
        }
        if (adapter.fetchMode() == JobFetchMode.SITEMAP && !configured) {
            return "Ready with per-search sitemap URLs, or add sitemapUrls defaults in job_sources.adapter_config.";
        }
        if (adapter.fetchMode() == JobFetchMode.USER_URL || adapter.fetchMode() == JobFetchMode.STRUCTURED_DATA) {
            return "Ready when the user provides a job URL.";
        }
        if (adapter.fetchMode() == JobFetchMode.MANUAL) {
            return "Ready when the user pastes a job description.";
        }
        return "Ready.";
    }

    private String targetHint(JobSource source, JobSourceAdapter adapter) {
        if (adapter == null) return null;
        return switch (source.getSourceKey()) {
            case "greenhouse" -> "Greenhouse board token";
            case "lever" -> "Lever company slug";
            case "ashby" -> "Ashby job board name";
            case "sitemap" -> "Sitemap URL";
            case "user_url", "structured_data" -> "Job posting URL";
            default -> adapter.fetchMode() == JobFetchMode.MANUAL ? "Pasted job description" : null;
        };
    }

    private String targetExample(JobSource source, JobSourceAdapter adapter) {
        if (adapter == null) return null;
        return switch (source.getSourceKey()) {
            case "greenhouse" -> "airbnb";
            case "lever" -> "netflix";
            case "ashby" -> "openai";
            case "sitemap" -> "https://example.com/sitemap.xml";
            case "user_url", "structured_data" -> "https://example.com/jobs/frontend-engineer";
            default -> null;
        };
    }
}
