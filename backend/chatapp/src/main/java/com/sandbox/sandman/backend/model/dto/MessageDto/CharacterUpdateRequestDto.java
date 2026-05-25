package com.sandbox.sandman.backend.model.dto.MessageDto;

import lombok.Data;

@Data
public class CharacterUpdateRequestDto {
    private String aiName;
    private String avatarUrl;
    private String appearanceReferenceUrl;
    private String appearanceReferenceObjectPath;
    private String role;
    private String character;
    private String biography;
    private String rule;
    private String posterUrl;
    private String visibility;
    private String styleExamples;
    private Boolean imageEnabled;
    private String imageTriggerRules;
    private String imagePromptTemplate;
    private String fineTuneStatus;
    private String fineTunedModelId;
    private Boolean personaFeedEnabled;
    private Integer personaFeedMinIntervalHours;
    private Integer personaFeedMaxIntervalHours;
    private String personaFeedWindowStart;
    private String personaFeedWindowEnd;
    private String personaFeedTimezone;
}
