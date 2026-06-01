package com.sandbox.sandman.backend.adapters;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Component
public class SitemapAdapter implements JobSourceAdapter {
    private static final int MAX_SITEMAPS_PER_SEARCH = 8;
    private static final int MAX_URLS_PER_SEARCH = 100;

    private final HtmlJobExtractor htmlJobExtractor;
    private final HttpClient httpClient;

    public SitemapAdapter(HtmlJobExtractor htmlJobExtractor) {
        this.htmlJobExtractor = htmlJobExtractor;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String sourceKey() {
        return "sitemap";
    }

    @Override
    public JobFetchMode fetchMode() {
        return JobFetchMode.SITEMAP;
    }

    @Override
    public List<ExternalJobRef> search(JobSearchCriteria criteria) {
        List<String> sitemapUrls = AdapterSupport.configuredBoards(criteria, "sitemapUrls", "urls", "targets");
        if (sitemapUrls.isEmpty()) return List.of();

        int limit = criteria == null || criteria.limit() == null ? 10 : Math.max(1, criteria.limit());
        int candidateLimit = Math.min(MAX_URLS_PER_SEARCH, Math.max(limit * 4, limit));
        List<String> candidates = collectCandidateUrls(sitemapUrls, candidateLimit);
        List<String> preferred = preferQueryMatches(candidates, criteria == null ? null : criteria.query());
        return preferred.stream()
                .limit(limit)
                .map(url -> new ExternalJobRef(AdapterSupport.canonicalJobUrl(url), url, "Sitemap job", hostLabel(url), criteria == null ? null : criteria.locationText(),
                        Map.of("sourceSitemaps", sitemapUrls)))
                .toList();
    }

    @Override
    public RawJobPayload fetchDetail(ExternalJobRef ref) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", ref.url());
        payload.put("title", ref.title());
        payload.put("company", ref.company());
        payload.put("locationText", ref.locationText());
        payload.put("metadata", ref.metadata());
        payload.put("importMode", "SITEMAP");
        try {
            HttpResponse<String> response = fetch(ref.url(), Duration.ofSeconds(15));
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
        String url = AdapterSupport.canonicalJobUrl(firstNonBlank(string(payload.get("finalUrl")), string(payload.get("url")), raw.url()));
        String host = hostLabel(url);
        return new NormalizedJobRecord(
                raw.sourceJobKey(),
                url,
                firstNonBlank(string(payload.get("title")), "Sitemap job from " + host),
                firstNonBlank(string(payload.get("company")), host),
                firstNonBlank(string(payload.get("locationText")), "Location not provided"),
                null,
                null,
                intValue(payload.get("salaryMin")),
                intValue(payload.get("salaryMax")),
                firstNonBlank(string(payload.get("currency")), "THB"),
                string(payload.get("employmentType")),
                string(payload.get("workplaceType")),
                stringList(payload.get("skills")),
                firstNonBlank(string(payload.get("description")), "Imported from a sitemap URL and normalized from the public job page."),
                AdapterSupport.canonicalJobUrl(url),
                null
        );
    }

    List<String> urlsFromSitemapXml(String xml) {
        Document doc = Jsoup.parse(xml == null ? "" : xml, "", Parser.xmlParser());
        return doc.select("url > loc").eachText().stream()
                .map(String::trim)
                .filter(url -> !url.isBlank())
                .distinct()
                .toList();
    }

    List<String> sitemapIndexUrlsFromXml(String xml) {
        Document doc = Jsoup.parse(xml == null ? "" : xml, "", Parser.xmlParser());
        return doc.select("sitemap > loc").eachText().stream()
                .map(String::trim)
                .filter(url -> !url.isBlank())
                .distinct()
                .toList();
    }

    private List<String> collectCandidateUrls(List<String> sitemapUrls, int maxUrls) {
        LinkedHashSet<String> allUrls = new LinkedHashSet<>();
        LinkedHashSet<String> likelyJobUrls = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(sitemapUrls);
        Set<String> visitedSitemaps = new HashSet<>();
        int fetchedSitemaps = 0;

        while (!queue.isEmpty() && fetchedSitemaps < MAX_SITEMAPS_PER_SEARCH && likelyJobUrls.size() < maxUrls) {
            String sitemapUrl = queue.removeFirst();
            if (sitemapUrl == null || sitemapUrl.isBlank() || !visitedSitemaps.add(sitemapUrl)) continue;
            fetchedSitemaps++;
            try {
                HttpResponse<String> response = fetch(sitemapUrl.trim(), Duration.ofSeconds(15));
                if (response.statusCode() < 200 || response.statusCode() >= 300) continue;
                String xml = response.body();
                sitemapIndexUrlsFromXml(xml).stream()
                        .filter(url -> !visitedSitemaps.contains(url))
                        .limit(MAX_SITEMAPS_PER_SEARCH)
                        .forEach(queue::addLast);
                for (String url : urlsFromSitemapXml(xml)) {
                    if (allUrls.size() >= maxUrls && likelyJobUrls.size() >= maxUrls) break;
                    String canonicalUrl = AdapterSupport.canonicalJobUrl(url);
                    allUrls.add(canonicalUrl);
                    if (isLikelyJobUrl(canonicalUrl)) likelyJobUrls.add(canonicalUrl);
                }
            } catch (Exception ignored) {
                // One broken sitemap should not fail the entire search run.
            }
        }

        LinkedHashSet<String> selected = likelyJobUrls.isEmpty() ? allUrls : likelyJobUrls;
        return selected.stream().limit(maxUrls).toList();
    }

    private HttpResponse<String> fetch(String url, Duration timeout) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", "JOBJAB/1.0 (+https://sandbox.local)")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private List<String> preferQueryMatches(List<String> candidates, String query) {
        if (query == null || query.isBlank()) return candidates;
        List<String> matched = candidates.stream()
                .filter(url -> containsAllTerms(url.toLowerCase(Locale.ROOT), query))
                .toList();
        return matched.isEmpty() ? candidates : matched;
    }

    private boolean containsAllTerms(String haystack, String query) {
        return Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(term -> term.length() > 1)
                .allMatch(haystack::contains);
    }

    private boolean isLikelyJobUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("job")
                || lower.contains("career")
                || lower.contains("position")
                || lower.contains("opening")
                || lower.contains("vacanc")
                || lower.contains("work-with-us")
                || lower.contains("greenhouse")
                || lower.contains("lever")
                || lower.contains("ashby");
    }

    private String rawText(ExtractedJobPage extracted) {
        return String.join("\n",
                firstNonBlank(extracted.title(), ""),
                firstNonBlank(extracted.company(), ""),
                firstNonBlank(extracted.locationText(), ""),
                firstNonBlank(extracted.description(), "")
        ).trim();
    }

    private String hostLabel(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception ignored) {
            return "external source";
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
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
}
