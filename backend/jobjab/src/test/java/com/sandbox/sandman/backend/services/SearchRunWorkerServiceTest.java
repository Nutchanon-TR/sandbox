package com.sandbox.sandman.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sandbox.sandman.backend.adapters.*;
import com.sandbox.sandman.backend.model.dto.SearchRunRequest;
import com.sandbox.sandman.backend.model.entity.Job;
import com.sandbox.sandman.backend.model.entity.JobSource;
import com.sandbox.sandman.backend.model.entity.RawJobSnapshot;
import com.sandbox.sandman.backend.model.entity.SearchRun;
import com.sandbox.sandman.backend.repositories.RawJobSnapshotRepository;
import com.sandbox.sandman.backend.repositories.SearchRunEventRepository;
import com.sandbox.sandman.backend.repositories.SearchRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class SearchRunWorkerServiceTest {

    @Test
    void capsAutoAiAnalysisDuringBulkSearch() {
        JobSourceAdapter adapter = mock(JobSourceAdapter.class);
        SearchRunRepository searchRunRepository = mock(SearchRunRepository.class);
        SearchRunEventRepository eventRepository = mock(SearchRunEventRepository.class);
        RawJobSnapshotRepository snapshotRepository = mock(RawJobSnapshotRepository.class);
        JobSourceService jobSourceService = mock(JobSourceService.class);
        JobService jobService = mock(JobService.class);
        MatchAnalysisService matchAnalysisService = mock(MatchAnalysisService.class);
        SearchRunWorkerService service = new SearchRunWorkerService(
                List.of(adapter),
                searchRunRepository,
                eventRepository,
                snapshotRepository,
                jobSourceService,
                jobService,
                matchAnalysisService,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "autoAnalyzeLimit", 2);

        SearchRun run = new SearchRun();
        run.setId(10L);
        run.setUserId(7L);
        run.setRequestedLimit(3);
        run.setSourceKeys(List.of("manual_jd"));
        when(searchRunRepository.findById(10L)).thenReturn(Optional.of(run));

        when(adapter.sourceKey()).thenReturn("manual_jd");
        when(adapter.fetchMode()).thenReturn(JobFetchMode.MANUAL);
        when(adapter.search(any())).thenReturn(List.of(ref("one"), ref("two"), ref("three")));
        when(adapter.fetchDetail(any())).thenAnswer(invocation -> {
            ExternalJobRef ref = invocation.getArgument(0);
            return new RawJobPayload(ref.sourceJobKey(), ref.url(), "JD " + ref.title(), Map.of("title", ref.title()));
        });
        when(adapter.normalize(any())).thenAnswer(invocation -> {
            RawJobPayload raw = invocation.getArgument(0);
            return new NormalizedJobRecord(
                    raw.sourceJobKey(),
                    raw.url(),
                    raw.payload().get("title").toString(),
                    "Acme",
                    "Bangkok",
                    null,
                    null,
                    null,
                    null,
                    "THB",
                    "Full-time",
                    "Hybrid",
                    List.of("Java"),
                    raw.rawText(),
                    raw.url(),
                    null
            );
        });

        JobSource source = new JobSource();
        source.setId(5L);
        source.setSourceKey("manual_jd");
        source.setAdapterConfig(Map.of());
        when(jobSourceService.ensureSource(adapter)).thenReturn(source);
        when(snapshotRepository.findBySourceIdAndSourceJobKeyAndContentHash(anyLong(), any(), any())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(RawJobSnapshot.class))).thenAnswer(invocation -> {
            RawJobSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(100L);
            return snapshot;
        });
        AtomicLong jobIds = new AtomicLong(20);
        when(jobService.upsert(anyLong(), anyLong(), any())).thenAnswer(invocation -> {
            NormalizedJobRecord normalized = invocation.getArgument(2);
            Job job = new Job();
            job.setId(jobIds.incrementAndGet());
            job.setTitle(normalized.title());
            return job;
        });

        service.execute(10L, new SearchRunRequest("Java", "Bangkok", 3, null, List.of(), List.of(), Map.of(), List.of("manual_jd")));

        verify(matchAnalysisService, times(2)).analyze(eq(7L), anyLong());
        verify(searchRunRepository, atLeastOnce()).save(run);
        verify(eventRepository, atLeastOnce()).save(any());
    }

    @Test
    void resultLimitAppliesAcrossAllSourcesInOneRun() {
        JobSourceAdapter firstAdapter = mock(JobSourceAdapter.class);
        JobSourceAdapter secondAdapter = mock(JobSourceAdapter.class);
        SearchRunRepository searchRunRepository = mock(SearchRunRepository.class);
        SearchRunEventRepository eventRepository = mock(SearchRunEventRepository.class);
        RawJobSnapshotRepository snapshotRepository = mock(RawJobSnapshotRepository.class);
        JobSourceService jobSourceService = mock(JobSourceService.class);
        JobService jobService = mock(JobService.class);
        MatchAnalysisService matchAnalysisService = mock(MatchAnalysisService.class);
        SearchRunWorkerService service = new SearchRunWorkerService(
                List.of(firstAdapter, secondAdapter),
                searchRunRepository,
                eventRepository,
                snapshotRepository,
                jobSourceService,
                jobService,
                matchAnalysisService,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "autoAnalyzeLimit", 10);

        SearchRun run = new SearchRun();
        run.setId(11L);
        run.setUserId(7L);
        run.setRequestedLimit(2);
        run.setSourceKeys(List.of("manual_jd", "user_url"));
        when(searchRunRepository.findById(11L)).thenReturn(Optional.of(run));

        stubAdapter(firstAdapter, "manual_jd", List.of(ref("manual-one")));
        stubAdapter(secondAdapter, "user_url", List.of(ref("url-one"), ref("url-two"), ref("url-three")));
        when(jobSourceService.ensureSource(any())).thenAnswer(invocation -> {
            JobSourceAdapter adapter = invocation.getArgument(0);
            JobSource source = new JobSource();
            source.setId("manual_jd".equals(adapter.sourceKey()) ? 5L : 6L);
            source.setSourceKey(adapter.sourceKey());
            source.setAdapterConfig(Map.of());
            return source;
        });
        when(snapshotRepository.findBySourceIdAndSourceJobKeyAndContentHash(anyLong(), any(), any())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(RawJobSnapshot.class))).thenAnswer(invocation -> {
            RawJobSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(100L);
            return snapshot;
        });
        AtomicLong jobIds = new AtomicLong(20);
        when(jobService.upsert(anyLong(), anyLong(), any())).thenAnswer(invocation -> {
            NormalizedJobRecord normalized = invocation.getArgument(2);
            Job job = new Job();
            job.setId(jobIds.incrementAndGet());
            job.setTitle(normalized.title());
            return job;
        });

        service.execute(11L, new SearchRunRequest("Java", "Bangkok", 2, null, List.of(), List.of(), Map.of(), List.of("manual_jd", "user_url")));

        verify(jobService, times(2)).upsert(anyLong(), anyLong(), any());
        verify(secondAdapter, times(1)).fetchDetail(any());
        verify(matchAnalysisService, times(2)).analyze(eq(7L), anyLong());
    }

    @Test
    void sourceFailureDoesNotStopRemainingSources() {
        JobSourceAdapter failingAdapter = mock(JobSourceAdapter.class);
        JobSourceAdapter workingAdapter = mock(JobSourceAdapter.class);
        SearchRunRepository searchRunRepository = mock(SearchRunRepository.class);
        SearchRunEventRepository eventRepository = mock(SearchRunEventRepository.class);
        RawJobSnapshotRepository snapshotRepository = mock(RawJobSnapshotRepository.class);
        JobSourceService jobSourceService = mock(JobSourceService.class);
        JobService jobService = mock(JobService.class);
        MatchAnalysisService matchAnalysisService = mock(MatchAnalysisService.class);
        SearchRunWorkerService service = new SearchRunWorkerService(
                List.of(failingAdapter, workingAdapter),
                searchRunRepository,
                eventRepository,
                snapshotRepository,
                jobSourceService,
                jobService,
                matchAnalysisService,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "autoAnalyzeLimit", 10);

        SearchRun run = new SearchRun();
        run.setId(12L);
        run.setUserId(7L);
        run.setRequestedLimit(3);
        run.setSourceKeys(List.of("broken", "manual_jd"));
        when(searchRunRepository.findById(12L)).thenReturn(Optional.of(run));

        when(failingAdapter.sourceKey()).thenReturn("broken");
        when(failingAdapter.fetchMode()).thenReturn(JobFetchMode.OFFICIAL_API);
        when(workingAdapter.sourceKey()).thenReturn("manual_jd");
        stubAdapter(workingAdapter, "manual_jd", List.of(ref("manual-one")));
        when(jobSourceService.ensureSource(failingAdapter)).thenThrow(new IllegalStateException("platform unavailable"));
        when(jobSourceService.ensureSource(workingAdapter)).thenAnswer(invocation -> {
            JobSource source = new JobSource();
            source.setId(5L);
            source.setSourceKey("manual_jd");
            source.setAdapterConfig(Map.of());
            return source;
        });
        when(snapshotRepository.findBySourceIdAndSourceJobKeyAndContentHash(anyLong(), any(), any())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(RawJobSnapshot.class))).thenAnswer(invocation -> {
            RawJobSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(100L);
            return snapshot;
        });
        when(jobService.upsert(anyLong(), anyLong(), any())).thenAnswer(invocation -> {
            NormalizedJobRecord normalized = invocation.getArgument(2);
            Job job = new Job();
            job.setId(30L);
            job.setTitle(normalized.title());
            return job;
        });

        service.execute(12L, new SearchRunRequest("Java", "Bangkok", 3, null, List.of(), List.of(), Map.of(), List.of("broken", "manual_jd")));

        verify(jobService).upsert(anyLong(), anyLong(), any());
        verify(matchAnalysisService).analyze(7L, 30L);
        verify(searchRunRepository, atLeastOnce()).save(run);
        org.assertj.core.api.Assertions.assertThat(run.getStatus()).isEqualTo("PARTIAL");
    }

    private ExternalJobRef ref(String key) {
        return new ExternalJobRef(key, "https://example.com/jobs/" + key, key, "Acme", "Bangkok");
    }

    private void stubAdapter(JobSourceAdapter adapter, String sourceKey, List<ExternalJobRef> refs) {
        when(adapter.sourceKey()).thenReturn(sourceKey);
        when(adapter.fetchMode()).thenReturn(JobFetchMode.MANUAL);
        when(adapter.search(any())).thenReturn(refs);
        when(adapter.fetchDetail(any())).thenAnswer(invocation -> {
            ExternalJobRef ref = invocation.getArgument(0);
            return new RawJobPayload(ref.sourceJobKey(), ref.url(), "JD " + ref.title(), Map.of("title", ref.title()));
        });
        when(adapter.normalize(any())).thenAnswer(invocation -> {
            RawJobPayload raw = invocation.getArgument(0);
            return new NormalizedJobRecord(
                    raw.sourceJobKey(),
                    raw.url(),
                    raw.payload().get("title").toString(),
                    "Acme",
                    "Bangkok",
                    null,
                    null,
                    null,
                    null,
                    "THB",
                    "Full-time",
                    "Hybrid",
                    List.of("Java"),
                    raw.rawText(),
                    raw.url(),
                    null
            );
        });
    }
}
