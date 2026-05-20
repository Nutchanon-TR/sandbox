export type CharacterFormValues = {
    aiName: string;
    avatarUrl?: string;
    posterUrl?: string;
    visibility: string;
    role?: string;
    character?: string;
    biography?: string;
    rule?: string;
    styleExamples?: string;
    imageEnabled: boolean;
    photoKeywords?: string;
    activityKeywords?: string;
    imagePromptTemplate?: string;
};

export type CharacterPayload = {
    aiName: string;
    avatarUrl: string | null;
    posterUrl: string | null;
    visibility: string;
    role: string | null;
    character: string | null;
    biography: string | null;
    rule: string | null;
    styleExamples: string;
    imageEnabled: boolean;
    imageTriggerRules: string;
    imagePromptTemplate: string | null;
};
