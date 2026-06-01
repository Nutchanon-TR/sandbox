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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GreenhouseAdapter implements JobSourceAdapter {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GreenhouseAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String sourceKey() {
        return "greenhouse";
    }

    @Override
    public JobFetchMode fetchMode() {
        return JobFetchMode.OFFICIAL_API;
    }

    @Override
    public List<ExternalJobRef> search(JobSearchCriteria criteria) {
        List<String> boards = AdapterSupport.configuredBoards(criteria, "boards", "boardTokens", "companies");
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
            String url = "https://boards-api.greenhouse.io/v1/boards/%s/jobs/%s?content=true"
                    .formatted(encode(board), encode(jobId));
            JsonNode node = getJson(url);
            Map<String, Object> payload = AdapterSupport.mapOf(node, objectMapper);
            payload.put("boardToken", board);
            payload.put("url", ref.url());
            payload.put("sourceApiUrl", url);
            return new RawJobPayload(ref.sourceJobKey(), ref.url(), AdapterSupport.htmlToText(AdapterSupport.text(node, "content")), payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to fetch Greenhouse job detail", ex);
        }
    }

    @Override
    public NormalizedJobRecord normalize(RawJobPayload raw) {
        JsonNode node = objectMapper.valueToTree(raw.payload());
        String title = AdapterSupport.text(node, "title");
        String company = AdapterSupport.text(node, "boardToken");
        String location = nestedText(node, "location", "name");
        String content = AdapterSupport.htmlToText(AdapterSupport.text(node, "content"));
        return new NormalizedJobRecord(
                raw.sourceJobKey(),
                raw.url(),
                AdapterSupport.firstNonBlank(title, "Greenhouse job"),
                AdapterSupport.firstNonBlank(company, "Greenhouse"),
                location,
                null,
                null,
                null,
                null,
                "THB",
                null,
                null,
                AdapterSupport.inferSkills(title + " " + content),
                content,
                raw.url(),
                null
        );
    }

    private List<ExternalJobRef> fetchBoardRefs(String board, JobSearchCriteria criteria, int remaining) {
        if (remaining <= 0) return List.of();
        try {
            String url = "https://boards-api.greenhouse.io/v1/boards/%s/jobs?content=true".formatted(encode(board));
            JsonNode root = getJson(url);
            JsonNode jobs = root.path("jobs");
            List<ExternalJobRef> refs = new ArrayList<>();
            if (jobs.isArray()) {
                for (JsonNode job : jobs) {
                    String id = AdapterSupport.text(job, "id");
                    String title = AdapterSupport.text(job, "title");
                    String location = nestedText(job, "location", "name");
                    String absoluteUrl = AdapterSupport.text(job, "absolute_url");
                    String searchable = title + " " + location + " " + AdapterSupport.htmlToText(AdapterSupport.text(job, "content"));
                    if (id != null && AdapterSupport.matches(criteria, searchable)) {
                        refs.add(new ExternalJobRef(board + ":" + id, absoluteUrl, title, board, location));
                        if (refs.size() >= remaining) break;
                    }
                }
            }
            return refs;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "JOBJAB/1.0")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Greenhouse returned HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private String nestedText(JsonNode node, String parent, String field) {
        JsonNode child = node == null ? null : node.get(parent);
        return AdapterSupport.text(child, field);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
