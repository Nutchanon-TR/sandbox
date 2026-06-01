package com.sandbox.sandman.backend.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.Jsoup;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

final class AdapterSupport {
    private static final List<String> KNOWN_SKILLS = List.of(
            "JavaScript", "TypeScript", "React", "Next.js", "Vue", "Angular", "Node.js",
            "Java", "Spring Boot", "SQL", "PostgreSQL", "MySQL", "Python", "Django",
            "Docker", "Kubernetes", "AWS", "GCP", "Azure", "Git", "REST API", "GraphQL",
            "Tailwind", "Ant Design", "Supabase", "Figma", "Agile", "Scrum"
    );

    private AdapterSupport() {
    }

    static List<String> configuredBoards(JobSearchCriteria criteria, String... keys) {
        if (criteria == null || criteria.sourceConfig() == null) return List.of();
        for (String key : keys) {
            Object value = criteria.sourceConfig().get(key);
            List<String> result = stringList(value);
            if (!result.isEmpty()) return result;
        }
        return List.of();
    }

    static boolean matches(JobSearchCriteria criteria, String text) {
        String query = criteria == null ? null : criteria.query();
        String location = criteria == null ? null : criteria.locationText();
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return containsAllTerms(lower, query) && containsAllTerms(lower, location);
    }

    static String htmlToText(String html) {
        if (html == null || html.isBlank()) return "";
        return Jsoup.parse(html).text().replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    static List<String> inferSkills(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return KNOWN_SKILLS.stream()
                .filter(skill -> lower.contains(skill.toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();
    }

    static String text(JsonNode node, String field) {
        JsonNode child = node == null ? null : node.get(field);
        if (child == null || child.isNull()) return null;
        String value = child.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    static Integer intValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isNumber()) return node.asInt();
        try {
            return (int) Math.round(Double.parseDouble(node.asText()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static ZonedDateTime epochMillis(JsonNode node) {
        if (node == null || !node.isNumber()) return null;
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(node.asLong()), ZoneId.of("UTC"));
    }

    static Map<String, Object> mapOf(JsonNode node, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        if (node == null || node.isNull()) return Map.of();
        return objectMapper.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    static String canonicalJobUrl(String url) {
        if (url == null || url.isBlank()) return url;
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            String path = normalizedPath(uri.getRawPath());
            String query = canonicalQuery(uri.getRawQuery());
            URI normalized = new URI(scheme, uri.getUserInfo(), host, defaultPort(scheme, port), path, query, null);
            return normalized.toString();
        } catch (Exception ignored) {
            return url.trim();
        }
    }

    private static int defaultPort(String scheme, int port) {
        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            return -1;
        }
        return port;
    }

    private static String normalizedPath(String path) {
        if (path == null || path.isBlank()) return "";
        String next = path.replaceAll("/{2,}", "/");
        if (next.length() > 1 && next.endsWith("/")) {
            return next.substring(0, next.length() - 1);
        }
        return next;
    }

    private static String canonicalQuery(String query) {
        if (query == null || query.isBlank()) return null;
        List<String> retained = Arrays.stream(query.split("&"))
                .filter(param -> !param.isBlank())
                .filter(param -> !isTrackingParam(param))
                .sorted()
                .toList();
        return retained.isEmpty() ? null : String.join("&", retained);
    }

    private static boolean isTrackingParam(String param) {
        String key = param.split("=", 2)[0].toLowerCase(Locale.ROOT);
        return key.startsWith("utm_")
                || key.equals("fbclid")
                || key.equals("gclid")
                || key.equals("msclkid")
                || key.equals("igshid")
                || key.equals("ref")
                || key.equals("source");
    }

    private static boolean containsAllTerms(String haystack, String needle) {
        if (needle == null || needle.isBlank()) return true;
        return Arrays.stream(needle.toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(term -> term.length() > 1)
                .allMatch(haystack::contains);
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item != null && !String.valueOf(item).isBlank())
                    .map(item -> String.valueOf(item).trim())
                    .distinct()
                    .toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Arrays.stream(text.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .toList();
        }
        return List.of();
    }
}
