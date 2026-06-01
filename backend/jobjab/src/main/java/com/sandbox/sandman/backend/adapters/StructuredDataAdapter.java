package com.sandbox.sandman.backend.adapters;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StructuredDataAdapter implements JobSourceAdapter {
    private final HtmlJobExtractor htmlJobExtractor;
    private final HttpClient httpClient;

    public StructuredDataAdapter(HtmlJobExtractor htmlJobExtractor) {
        this.htmlJobExtractor = htmlJobExtractor;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String sourceKey() {
        return "structured_data";
    }

    @Override
    public JobFetchMode fetchMode() {
        return JobFetchMode.STRUCTURED_DATA;
    }

    @Override
    public List<ExternalJobRef> search(JobSearchCriteria criteria) {
        if (criteria == null) return List.of();
        return urls(criteria).stream()
                .limit(criteria.limit() == null ? 10 : Math.max(1, criteria.limit()))
                .map(url -> new ExternalJobRef(AdapterSupport.canonicalJobUrl(url), url, "Structured JobPosting", null, criteria.locationText()))
                .toList();
    }

    @Override
    public RawJobPayload fetchDetail(ExternalJobRef ref) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", ref.url());
        payload.put("title", ref.title());
        payload.put("company", ref.company());
        payload.put("locationText", ref.locationText());
        payload.put("importMode", "STRUCTURED_JOB_POSTING");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ref.url()))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "JOBJAB/1.0 (+https://sandbox.local)")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            payload.put("httpStatus", response.statusCode());
            payload.put("finalUrl", response.uri().toString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                ExtractedJobPage extracted = htmlJobExtractor.extract(response.body(), response.uri().toString(), ref.title(), ref.locationText());
                payload.putAll(extracted.payload());
                payload.put("extractionStatus", "OK");
                return new RawJobPayload(ref.sourceJobKey(), response.uri().toString(), rawText(extracted), payload);
            }
            payload.put("extractionStatus", "HTTP_" + response.statusCode());
        } catch (Exception ex) {
            payload.put("extractionStatus", "ERROR");
            payload.put("extractionError", ex.getMessage());
        }
        return new RawJobPayload(ref.sourceJobKey(), ref.url(), ref.title() + "\n" + ref.url(), payload);
    }

    @Override
    public NormalizedJobRecord normalize(RawJobPayload raw) {
        Map<String, Object> payload = raw.payload();
        String url = AdapterSupport.canonicalJobUrl(string(payload.get("finalUrl")));
        if (url == null || url.isBlank()) url = AdapterSupport.canonicalJobUrl(string(payload.get("url")));
        String host = hostOf(url);
        return new NormalizedJobRecord(
                raw.sourceJobKey(),
                url,
                fallback(string(payload.get("title")), "Structured job from " + host),
                fallback(string(payload.get("company")), host),
                fallback(string(payload.get("locationText")), "Location not provided"),
                null,
                null,
                intValue(payload.get("salaryMin")),
                intValue(payload.get("salaryMax")),
                fallback(string(payload.get("currency")), "THB"),
                string(payload.get("employmentType")),
                string(payload.get("workplaceType")),
                stringList(payload.get("skills")),
                fallback(string(payload.get("description")), "Imported from schema.org JobPosting structured data."),
                AdapterSupport.canonicalJobUrl(url),
                null
        );
    }

    private String rawText(ExtractedJobPage extracted) {
        return String.join("\n",
                fallback(extracted.title(), ""),
                fallback(extracted.company(), ""),
                fallback(extracted.locationText(), ""),
                fallback(extracted.description(), "")
        ).trim();
    }

    private String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception ignored) {
            return "external source";
        }
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intValue(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return null;
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item != null && !String.valueOf(item).isBlank())
                    .map(String::valueOf)
                    .distinct()
                    .toList();
        }
        return List.of();
    }

    private List<String> urls(JobSearchCriteria criteria) {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
        if (criteria.jobUrl() != null && !criteria.jobUrl().isBlank()) {
            values.add(AdapterSupport.canonicalJobUrl(criteria.jobUrl()));
        }
        if (criteria.jobUrls() != null) {
            criteria.jobUrls().stream()
                    .filter(url -> url != null && !url.isBlank())
                    .map(AdapterSupport::canonicalJobUrl)
                    .forEach(values::add);
        }
        return values.stream().toList();
    }
}
