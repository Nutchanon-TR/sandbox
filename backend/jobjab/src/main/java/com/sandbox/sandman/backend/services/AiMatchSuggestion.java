package com.sandbox.sandman.backend.services;

import java.util.List;

public record AiMatchSuggestion(
        Integer matchScore,
        List<String> matchedSkills,
        List<String> missingSkills,
        List<String> redFlags,
        String summary
) {
}
