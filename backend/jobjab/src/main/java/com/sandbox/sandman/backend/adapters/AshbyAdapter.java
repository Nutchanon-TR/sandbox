package com.sandbox.sandman.backend.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AshbyAdapter implements JobSourceAdapter {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AshbyAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String sourceKey() {
        return "ashby";
    }

    @Override
    public JobFetchMode fetchMode() {
        return JobFetchMode.OFFICIAL_API;
    }

    @Override
    public List<ExternalJobRef> search(JobSearchCriteria criteria) {
        List<String> boards = AdapterSupport.configuredBoards(criteria, "boards", "jobBoardNames", "companies");
        if (boards.isEmpty()) return List.of();
        List<ExternalJobRef> refs = new ArrayList<>();
        int limit = criteria.limit() == null ? 10 : Math.max(1, criteria.limit());
        for (String board : boards) {
            refs.addAll(fetchBoardRefs(board, criteria, limit - refs.size()));
            if (refs.size() >= limit) break;
        }
        return refs;
    }

    @Override
    public RawJobPayload fetchDetail(ExternalJobRef ref) {
        try {
            String[] parts = ref.sourceJobKey().split(":", 2);
            String board = parts[0];
            String jobId = parts.length > 1 ? parts[1] : ref.sourceJobKey();
            JsonNode jobs = getJson(board).path("jobs");
            if (jobs.isArray()) {
                for (JsonNode job : jobs) {
                    if (jobId.equals(AdapterSupport.text(job, "id"))) {
                        Map<String, Object> payload = AdapterSupport.mapOf(job, objectMapper);
                        payload.put("jobBoardName", board);
                        return new RawJobPayload(ref.sourceJobKey(), ref.url(), description(job), payload);
                    }
                }
            }
            throw new IllegalArgumentException("Ashby job not found");
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to fetch Ashby job detail", ex);
        }
    }

    @Override
    public NormalizedJobRecord normalize(RawJobPayload raw) {
        JsonNode node = objectMapper.valueToTree(raw.payload());
        String title = AdapterSupport.text(node, "title");
        String company = AdapterSupport.text(node, "jobBoardName");
        String location = AdapterSupport.text(node, "location");
        String description = description(node);
        Salary salary = salary(node);
        return new NormalizedJobRecord(
                raw.sourceJobKey(),
                AdapterSupport.firstNonBlank(AdapterSupport.text(node, "jobUrl"), raw.url()),
                AdapterSupport.firstNonBlank(title, "Ashby job"),
                AdapterSupport.firstNonBlank(company, "Ashby"),
                location,
                null,
                null,
                salary.min(),
                salary.max(),
                salary.currency(),
                AdapterSupport.text(node, "employmentType"),
                AdapterSupport.text(node, "workplaceType"),
                AdapterSupport.inferSkills(title + " " + description),
                description,
                AdapterSupport.firstNonBlank(AdapterSupport.text(node, "applyUrl"), AdapterSupport.text(node, "jobUrl"), raw.url()),
                null
        );
    }

    private List<ExternalJobRef> fetchBoardRefs(String board, JobSearchCriteria criteria, int remaining) {
        if (remaining <= 0) return List.of();
        try {
            JsonNode jobs = getJson(board).path("jobs");
            List<ExternalJobRef> refs = new ArrayList<>();
            if (jobs.isArray()) {
                for (JsonNode job : jobs) {
                    String id = AdapterSupport.text(job, "id");
                    String title = AdapterSupport.text(job, "title");
                    String location = AdapterSupport.text(job, "location");
                    String jobUrl = AdapterSupport.text(job, "jobUrl");
                    String searchable = title + " " + location + " " + AdapterSupport.text(job, "department") + " " + AdapterSupport.text(job, "team") + " " + description(job);
                    if (id != null && AdapterSupport.matches(criteria, searchable)) {
                        refs.add(new ExternalJobRef(board + ":" + id, jobUrl, title, board, location));
                        if (refs.size() >= remaining) break;
                    }
                }
            }
            return refs;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private JsonNode getJson(String board) throws Exception {
        String url = "https://api.ashbyhq.com/posting-api/job-board/%s?includeCompensation=true".formatted(encode(board));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "JOBJAB/1.0")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Ashby returned HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private String description(JsonNode node) {
        return AdapterSupport.firstNonBlank(
                AdapterSupport.htmlToText(AdapterSupport.text(node, "descriptionHtml")),
                AdapterSupport.text(node, "descriptionPlain"),
                AdapterSupport.text(node, "description"),
                AdapterSupport.text(node, "compensationTierSummary")
        );
    }

    private Salary salary(JsonNode node) {
        JsonNode compensation = node == null ? null : node.get("compensation");
        if (compensation == null || compensation.isNull()) return new Salary(null, null, "THB");
        String summary = AdapterSupport.firstNonBlank(
                AdapterSupport.text(compensation, "scrapeableCompensationSalarySummary"),
                AdapterSupport.text(compensation, "compensationTierSummary")
        );
        Integer min = null;
        Integer max = null;
        if (summary != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d[\\d,]*)").matcher(summary);
            if (matcher.find()) min = Integer.parseInt(matcher.group(1).replace(",", ""));
            if (matcher.find()) max = Integer.parseInt(matcher.group(1).replace(",", ""));
        }
        return new Salary(min, max, "THB");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record Salary(Integer min, Integer max, String currency) {
    }
}
