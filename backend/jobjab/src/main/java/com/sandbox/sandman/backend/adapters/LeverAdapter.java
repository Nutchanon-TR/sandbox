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
public class LeverAdapter implements JobSourceAdapter {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public LeverAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String sourceKey() {
        return "lever";
    }

    @Override
    public JobFetchMode fetchMode() {
        return JobFetchMode.OFFICIAL_API;
    }

    @Override
    public List<ExternalJobRef> search(JobSearchCriteria criteria) {
        List<String> companies = AdapterSupport.configuredBoards(criteria, "companies", "sites", "boards");
        if (companies.isEmpty()) return List.of();
        List<ExternalJobRef> refs = new ArrayList<>();
        int limit = criteria.limit() == null ? 10 : Math.max(1, criteria.limit());
        for (String company : companies) {
            refs.addAll(fetchCompanyRefs(company, criteria, limit - refs.size()));
            if (refs.size() >= limit) break;
        }
        return refs;
    }

    @Override
    public RawJobPayload fetchDetail(ExternalJobRef ref) {
        try {
            String[] parts = ref.sourceJobKey().split(":", 2);
            String company = parts[0];
            String postingId = parts.length > 1 ? parts[1] : ref.sourceJobKey();
            String url = "https://api.lever.co/v0/postings/%s/%s?mode=json".formatted(encode(company), encode(postingId));
            JsonNode node = getJson(url);
            Map<String, Object> payload = AdapterSupport.mapOf(node, objectMapper);
            payload.put("companySite", company);
            payload.put("sourceApiUrl", url);
            return new RawJobPayload(ref.sourceJobKey(), ref.url(), description(node), payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to fetch Lever job detail", ex);
        }
    }

    @Override
    public NormalizedJobRecord normalize(RawJobPayload raw) {
        JsonNode node = objectMapper.valueToTree(raw.payload());
        String title = AdapterSupport.text(node, "text");
        String company = AdapterSupport.text(node, "companySite");
        String location = nestedText(node, "categories", "location");
        String team = nestedText(node, "categories", "team");
        String commitment = nestedText(node, "categories", "commitment");
        String workplaceType = nestedText(node, "categories", "workplaceType");
        String description = description(node);
        return new NormalizedJobRecord(
                raw.sourceJobKey(),
                AdapterSupport.firstNonBlank(AdapterSupport.text(node, "hostedUrl"), raw.url()),
                AdapterSupport.firstNonBlank(title, "Lever job"),
                AdapterSupport.firstNonBlank(company, "Lever"),
                location,
                null,
                null,
                null,
                null,
                "THB",
                AdapterSupport.firstNonBlank(commitment, team),
                workplaceType,
                AdapterSupport.inferSkills(title + " " + description),
                description,
                AdapterSupport.firstNonBlank(AdapterSupport.text(node, "applyUrl"), AdapterSupport.text(node, "hostedUrl"), raw.url()),
                AdapterSupport.epochMillis(node.get("createdAt"))
        );
    }

    private List<ExternalJobRef> fetchCompanyRefs(String company, JobSearchCriteria criteria, int remaining) {
        if (remaining <= 0) return List.of();
        try {
            String url = "https://api.lever.co/v0/postings/%s?mode=json&limit=%d".formatted(encode(company), Math.max(remaining, 10));
            JsonNode jobs = getJson(url);
            List<ExternalJobRef> refs = new ArrayList<>();
            if (jobs.isArray()) {
                for (JsonNode job : jobs) {
                    String id = AdapterSupport.text(job, "id");
                    String title = AdapterSupport.text(job, "text");
                    String location = nestedText(job, "categories", "location");
                    String hostedUrl = AdapterSupport.text(job, "hostedUrl");
                    String searchable = title + " " + location + " " + nestedText(job, "categories", "team") + " " + description(job);
                    if (id != null && AdapterSupport.matches(criteria, searchable)) {
                        refs.add(new ExternalJobRef(company + ":" + id, hostedUrl, title, company, location));
                        if (refs.size() >= remaining) break;
                    }
                }
            }
            return refs;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String description(JsonNode node) {
        List<String> parts = new ArrayList<>();
        String plain = AdapterSupport.text(node, "descriptionPlain");
        if (plain != null) parts.add(plain);
        String html = AdapterSupport.text(node, "description");
        if (html != null) parts.add(AdapterSupport.htmlToText(html));
        JsonNode lists = node == null ? null : node.get("lists");
        if (lists != null && lists.isArray()) {
            for (JsonNode list : lists) {
                String heading = AdapterSupport.text(list, "text");
                String content = AdapterSupport.htmlToText(AdapterSupport.text(list, "content"));
                if (heading != null) parts.add(heading);
                if (content != null) parts.add(content);
            }
        }
        return String.join("\n", parts).trim();
    }

    private String nestedText(JsonNode node, String parent, String field) {
        JsonNode child = node == null ? null : node.get(parent);
        return AdapterSupport.text(child, field);
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
            throw new IllegalStateException("Lever returned HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
