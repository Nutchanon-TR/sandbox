package com.sandbox.sandman.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sandbox.sandman.backend.model.entity.Job;
import com.sandbox.sandman.backend.model.entity.UserJobProfile;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class JobjabAiClient {
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private ChatClient chatClient;

    @Value("${app.jobjab.ai.matching-enabled:true}")
    private boolean matchingEnabled;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @PostConstruct
    void init() {
        this.chatClient = chatClientBuilder.build();
    }

    public Optional<AiMatchSuggestion> analyze(Job job, UserJobProfile profile) {
        if (!matchingEnabled || apiKey == null || apiKey.isBlank() || "placeholder".equals(apiKey)) {
            return Optional.empty();
        }

        try {
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage("""
                    You are JOBJAB, a job matching assistant.
                    Compare the candidate profile with the job description.
                    Return JSON only with keys: matchScore, matchedSkills, missingSkills, redFlags, summary.
                    matchScore must be an integer 0-100.
                    redFlags should be warning tags, not long explanations.
                    summary should be concise Thai or English matching the job description language.
                    """));
            messages.add(new UserMessage("""
                    Candidate profile:
                    Headline: %s
                    Desired titles: %s
                    Skills: %s
                    Experiences: %s
                    Preferred locations: %s
                    Salary: %s-%s

                    Job:
                    Title: %s
                    Company: %s
                    Location: %s
                    Salary: %s-%s %s
                    Skills extracted: %s
                    Description: %s
                    """.formatted(
                    profile == null ? "" : safe(profile.getHeadline()),
                    profile == null ? List.of() : profile.getDesiredTitles(),
                    profile == null ? List.of() : profile.getSkills(),
                    profile == null ? List.of() : profile.getExperiences(),
                    profile == null ? List.of() : profile.getPreferredLocations(),
                    profile == null ? "" : profile.getSalaryMin(),
                    profile == null ? "" : profile.getSalaryMax(),
                    safe(job.getTitle()),
                    safe(job.getCompany()),
                    safe(job.getLocationText()),
                    job.getSalaryMin(),
                    job.getSalaryMax(),
                    safe(job.getCurrency()),
                    job.getSkills(),
                    truncate(safe(job.getDescription()), 8000)
            )));
            String content = chatClient.prompt(new Prompt(messages)).call().content();
            return parse(content);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<AiMatchSuggestion> parse(String content) {
        if (content == null || content.isBlank()) return Optional.empty();
        try {
            String json = extractJson(content);
            JsonNode node = objectMapper.readTree(json);
            return Optional.of(new AiMatchSuggestion(
                    clamp(node.path("matchScore").asInt(0)),
                    stringList(node.path("matchedSkills")),
                    stringList(node.path("missingSkills")),
                    stringList(node.path("redFlags")),
                    node.path("summary").asText("")
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) return content.substring(start, end + 1);
        return content;
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) values.add(item.asText().trim());
        }
        return values.stream().distinct().toList();
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
