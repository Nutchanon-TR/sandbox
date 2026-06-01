package com.sandbox.sandman.backend.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.*;

@Component
public class HtmlJobExtractor {
    private static final List<String> KNOWN_SKILLS = List.of(
            "JavaScript", "TypeScript", "React", "Next.js", "Vue", "Angular", "Node.js",
            "Java", "Spring Boot", "SQL", "PostgreSQL", "MySQL", "Python", "Django",
            "Docker", "Kubernetes", "AWS", "GCP", "Azure", "Git", "REST API", "GraphQL",
            "Tailwind", "Ant Design", "Supabase", "Figma", "Agile", "Scrum"
    );

    private final ObjectMapper objectMapper;

    public HtmlJobExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ExtractedJobPage extract(String html, String fallbackUrl, String queryFallback, String locationFallback) {
        Document doc = Jsoup.parse(html == null ? "" : html, fallbackUrl);
        JsonNode jobPosting = findJobPostingJsonLd(doc).orElse(null);
        String bodyText = clean(doc.body() == null ? "" : doc.body().text());
        String title = firstNonBlank(
                text(jobPosting, "title"),
                meta(doc, "property", "og:title"),
                meta(doc, "name", "twitter:title"),
                doc.title(),
                queryFallback,
                "Imported job"
        );
        String description = firstNonBlank(
                htmlText(jobPosting, "description"),
                meta(doc, "name", "description"),
                meta(doc, "property", "og:description"),
                bodyText
        );
        String company = firstNonBlank(
                nestedText(jobPosting, "hiringOrganization", "name"),
                meta(doc, "property", "og:site_name"),
                hostLabel(fallbackUrl),
                "Unknown company"
        );
        String location = firstNonBlank(locationFromJsonLd(jobPosting), locationFallback, "Location not provided");
        String employmentType = employmentType(jobPosting);
        String workplaceType = workplaceType(jobPosting, description);
        Salary salary = salary(jobPosting);
        ZonedDateTime postedAt = parseDate(text(jobPosting, "datePosted"));
        List<String> skills = inferSkills(title + " " + description + " " + bodyText);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", fallbackUrl);
        payload.put("title", title);
        payload.put("company", company);
        payload.put("locationText", location);
        payload.put("description", description);
        payload.put("employmentType", employmentType);
        payload.put("workplaceType", workplaceType);
        payload.put("salaryMin", salary.min());
        payload.put("salaryMax", salary.max());
        payload.put("currency", salary.currency());
        payload.put("postedAt", postedAt == null ? null : postedAt.toString());
        payload.put("skills", skills);
        payload.put("extractionMode", jobPosting == null ? "HTML_META" : "JSON_LD_JOB_POSTING");
        payload.put("pageTitle", doc.title());

        return new ExtractedJobPage(
                title,
                company,
                location,
                description,
                salary.min(),
                salary.max(),
                salary.currency(),
                employmentType,
                workplaceType,
                skills,
                postedAt,
                payload
        );
    }

    private Optional<JsonNode> findJobPostingJsonLd(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json], script[type='application/ld+json']")) {
            try {
                JsonNode root = objectMapper.readTree(script.data().isBlank() ? script.html() : script.data());
                JsonNode found = findJobPosting(root);
                if (found != null) return Optional.of(found);
            } catch (Exception ignored) {
                // Some pages include invalid or multi-object JSON-LD. Ignore and keep scanning.
            }
        }
        return Optional.empty();
    }

    private JsonNode findJobPosting(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findJobPosting(child);
                if (found != null) return found;
            }
            return null;
        }
        if (node.isObject()) {
            JsonNode type = node.get("@type");
            if (isJobPostingType(type)) return node;
            JsonNode graph = node.get("@graph");
            if (graph != null) return findJobPosting(graph);
        }
        return null;
    }

    private boolean isJobPostingType(JsonNode type) {
        if (type == null) return false;
        if (type.isTextual()) return "JobPosting".equalsIgnoreCase(type.asText());
        if (type.isArray()) {
            for (JsonNode item : type) {
                if (item.isTextual() && "JobPosting".equalsIgnoreCase(item.asText())) return true;
            }
        }
        return false;
    }

    private String locationFromJsonLd(JsonNode jobPosting) {
        JsonNode location = child(jobPosting, "jobLocation");
        if (location == null) return null;
        if (location.isArray() && !location.isEmpty()) location = location.get(0);
        JsonNode address = child(location, "address");
        if (address == null) return text(location, "name");
        return join(", ",
                text(address, "streetAddress"),
                text(address, "addressLocality"),
                text(address, "addressRegion"),
                text(address, "postalCode"),
                text(address, "addressCountry")
        );
    }

    private String employmentType(JsonNode jobPosting) {
        JsonNode node = child(jobPosting, "employmentType");
        if (node == null) return null;
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                if (item.isTextual()) values.add(item.asText());
            }
            return join(", ", values.toArray(String[]::new));
        }
        return node.isTextual() ? node.asText() : null;
    }

    private String workplaceType(JsonNode jobPosting, String description) {
        String text = (description == null ? "" : description).toLowerCase(Locale.ROOT);
        if (child(jobPosting, "jobLocationType") != null) return text(jobPosting, "jobLocationType");
        if (text.contains("remote")) return "REMOTE";
        if (text.contains("hybrid")) return "HYBRID";
        return null;
    }

    private Salary salary(JsonNode jobPosting) {
        JsonNode baseSalary = child(jobPosting, "baseSalary");
        if (baseSalary == null) return new Salary(null, null, "THB");
        String currency = firstNonBlank(text(baseSalary, "currency"), "THB");
        JsonNode value = child(baseSalary, "value");
        if (value == null) return new Salary(null, null, currency);
        Integer min = intText(value, "minValue");
        Integer max = intText(value, "maxValue");
        if (min == null && max == null) {
            Integer flat = intText(value, "value");
            min = flat;
            max = flat;
        }
        return new Salary(min, max, currency);
    }

    private List<String> inferSkills(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return KNOWN_SKILLS.stream()
                .filter(skill -> lower.contains(skill.toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();
    }

    private String meta(Document doc, String attr, String key) {
        Element element = doc.selectFirst("meta[" + attr + "='" + key + "']");
        return element == null ? null : clean(element.attr("content"));
    }

    private String htmlText(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : clean(Jsoup.parse(value).text());
    }

    private String nestedText(JsonNode node, String parent, String field) {
        return text(child(node, parent), field);
    }

    private String text(JsonNode node, String field) {
        JsonNode child = child(node, field);
        return child == null || child.isNull() ? null : clean(child.asText());
    }

    private Integer intText(JsonNode node, String field) {
        JsonNode child = child(node, field);
        if (child == null || child.isNull()) return null;
        if (child.isNumber()) return child.asInt();
        try {
            return (int) Math.round(Double.parseDouble(child.asText()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private JsonNode child(JsonNode node, String field) {
        return node == null ? null : node.get(field);
    }

    private ZonedDateTime parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ZonedDateTime.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private String join(String separator, String... values) {
        return Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.joining(separator));
    }

    private String hostLabel(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (Exception ignored) {
            return null;
        }
    }

    private record Salary(Integer min, Integer max, String currency) {
    }
}
