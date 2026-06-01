package com.sandbox.sandman.backend.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OfficialApiAdapterNormalizeTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void normalizesGreenhousePayload() {
        GreenhouseAdapter adapter = new GreenhouseAdapter(objectMapper);
        RawJobPayload raw = new RawJobPayload("demo:123", "https://boards.greenhouse.io/demo/jobs/123", "React TypeScript", Map.of(
                "id", 123,
                "boardToken", "demo",
                "title", "Frontend Engineer",
                "absolute_url", "https://boards.greenhouse.io/demo/jobs/123",
                "location", Map.of("name", "Bangkok"),
                "content", "<p>Build React and TypeScript products.</p>"
        ));

        NormalizedJobRecord result = adapter.normalize(raw);

        assertThat(result.title()).isEqualTo("Frontend Engineer");
        assertThat(result.company()).isEqualTo("demo");
        assertThat(result.locationText()).isEqualTo("Bangkok");
        assertThat(result.skills()).contains("React", "TypeScript");
        assertThat(result.description()).contains("Build React");
    }

    @Test
    void normalizesLeverPayload() {
        LeverAdapter adapter = new LeverAdapter(objectMapper);
        RawJobPayload raw = new RawJobPayload("demo:abc", "https://jobs.lever.co/demo/abc", "Java Spring Boot", Map.of(
                "id", "abc",
                "companySite", "demo",
                "text", "Backend Engineer",
                "hostedUrl", "https://jobs.lever.co/demo/abc",
                "categories", Map.of("location", "Remote", "team", "Engineering", "commitment", "Full-time"),
                "descriptionPlain", "Work on Java and Spring Boot APIs."
        ));

        NormalizedJobRecord result = adapter.normalize(raw);

        assertThat(result.title()).isEqualTo("Backend Engineer");
        assertThat(result.company()).isEqualTo("demo");
        assertThat(result.locationText()).isEqualTo("Remote");
        assertThat(result.skills()).contains("Java", "Spring Boot");
    }

    @Test
    void normalizesAshbyPayload() {
        AshbyAdapter adapter = new AshbyAdapter(objectMapper);
        RawJobPayload raw = new RawJobPayload("demo:job1", "https://jobs.ashbyhq.com/demo/job1", "Python SQL", Map.of(
                "id", "job1",
                "jobBoardName", "demo",
                "title", "Data Engineer",
                "location", "Bangkok",
                "jobUrl", "https://jobs.ashbyhq.com/demo/job1",
                "applyUrl", "https://jobs.ashbyhq.com/demo/job1/application",
                "descriptionHtml", "<p>Build Python and SQL pipelines.</p>",
                "compensation", Map.of("scrapeableCompensationSalarySummary", "THB 60000 - 90000")
        ));

        NormalizedJobRecord result = adapter.normalize(raw);

        assertThat(result.title()).isEqualTo("Data Engineer");
        assertThat(result.company()).isEqualTo("demo");
        assertThat(result.locationText()).isEqualTo("Bangkok");
        assertThat(result.skills()).contains("Python", "SQL");
    }
}
