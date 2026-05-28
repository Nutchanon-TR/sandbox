import type { Character } from '@/interface/ChatApp';
import type { CharacterFormValues, CharacterPayload } from './types';

export const DEFAULT_PHOTO_KEYWORDS = 'ถ่ายรูป, ส่งรูป, ขอดูรูป, ถ่ายมาให้ดู';
export const DEFAULT_ACTIVITY_KEYWORDS = 'ทำอะไรอยู่, ตอนนี้ทำไร, อยู่ไหน, ทำอะไรตอนนี้';
export const DEFAULT_AVATAR_URL = '/ai_avatar.png';
export const DEFAULT_PERSONA_FEED_TIMEZONE = 'Asia/Bangkok';
export const DEFAULT_PERSONALITY_TRAITS = '[]';

export const DEFAULT_FORM_VALUES: CharacterFormValues = {
    aiName: '',
    avatarUrl: '',
    appearanceReferenceUrl: '',
    appearanceReferenceObjectPath: '',
    posterUrl: '',
    visibility: 'private',
    role: '',
    character: '',
    personalityTraits: DEFAULT_PERSONALITY_TRAITS,
    biography: '',
    speechStyle: '',
    relationshipContext: '',
    memoryNotes: '',
    responseBoundaries: '',
    systemContext: '',
    rule: '',
    styleExamples: '[]',
    imageEnabled: true,
    photoKeywords: DEFAULT_PHOTO_KEYWORDS,
    activityKeywords: DEFAULT_ACTIVITY_KEYWORDS,
    imagePromptTemplate: '',
    personaFeedEnabled: false,
    personaFeedMinIntervalHours: 8,
    personaFeedMaxIntervalHours: 24,
    personaFeedWindowStart: '',
    personaFeedWindowEnd: '',
    personaFeedTimezone: DEFAULT_PERSONA_FEED_TIMEZONE,
};

export function getErrorMessage(error: unknown, fallbackMessage: string): string {
    if (typeof error === 'object' && error !== null && 'response' in error) {
        const response = (error as { response?: { data?: { message?: unknown } } }).response;
        if (typeof response?.data?.message === 'string') return response.data.message;
    }
    if (error instanceof Error) return error.message;
    return fallbackMessage;
}

export function formatStatus(status?: string | null): string {
    if (!status) return 'Not Started';
    return status
        .split('_')
        .filter(Boolean)
        .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
        .join(' ');
}

export function formatVisibilityLabel(visibility?: string | null): string {
    if (!visibility) return 'Private';
    return visibility.charAt(0).toUpperCase() + visibility.slice(1);
}

export function splitKeywords(value?: string): string[] {
    return (value ?? '')
        .split(/[\n,]/)
        .map((keyword) => keyword.trim())
        .filter(Boolean);
}

export function parseTriggerRules(json?: string | null) {
    if (!json) {
        return {
            photoKeywords: DEFAULT_PHOTO_KEYWORDS,
            activityKeywords: DEFAULT_ACTIVITY_KEYWORDS,
        };
    }

    try {
        const parsed = JSON.parse(json) as { photoKeywords?: string[]; activityKeywords?: string[] };
        return {
            photoKeywords: (parsed.photoKeywords?.length ? parsed.photoKeywords : splitKeywords(DEFAULT_PHOTO_KEYWORDS)).join(', '),
            activityKeywords: (parsed.activityKeywords?.length ? parsed.activityKeywords : splitKeywords(DEFAULT_ACTIVITY_KEYWORDS)).join(', '),
        };
    } catch {
        return {
            photoKeywords: DEFAULT_PHOTO_KEYWORDS,
            activityKeywords: DEFAULT_ACTIVITY_KEYWORDS,
        };
    }
}

export function normalizeJsonField(value?: string | null, fallback = '[]'): string {
    if (!value?.trim()) return fallback;

    try {
        return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
        return value.trim();
    }
}

function normalizeJsonForPayload(value?: string | null, fallback = '[]'): string {
    if (!value?.trim()) return fallback;
    return value.trim();
}

function appendPromptSection(sections: string[], title: string, value?: string | null) {
    if (!value?.trim()) return;
    sections.push(`[${title}]\n${value.trim()}`);
}

function combinePromptText(...values: Array<string | null | undefined>) {
    return values
        .map((value) => value?.trim())
        .filter(Boolean)
        .join('\n\n');
}

export function buildCompiledPromptPreview(values: Partial<CharacterFormValues>): string {
    const name = values.aiName?.trim() || 'AI Assistant';
    const sections: string[] = [];

    appendPromptSection(
        sections,
        'IDENTITY',
        `You are ${name}. Stay in character as ${name} throughout the conversation.`,
    );
    appendPromptSection(sections, 'ROLE', values.role);
    appendPromptSection(sections, 'CHARACTER AND PERSONALITY', values.character);
    const personalityTraits = normalizeJsonField(values.personalityTraits, DEFAULT_PERSONALITY_TRAITS);
    if (personalityTraits !== DEFAULT_PERSONALITY_TRAITS) {
        appendPromptSection(sections, 'PERSONALITY TRAITS', personalityTraits);
    }
    appendPromptSection(sections, 'BIOGRAPHY', values.biography);
    appendPromptSection(sections, 'RELATIONSHIP CONTEXT', values.relationshipContext);
    appendPromptSection(sections, 'MEMORY NOTES', values.memoryNotes);
    appendPromptSection(sections, 'SPEECH STYLE', values.speechStyle);
    appendPromptSection(
        sections,
        'RESPONSE BOUNDARIES AND RULES',
        combinePromptText(values.responseBoundaries, values.rule),
    );
    const styleExamples = normalizeJsonField(values.styleExamples, '[]');
    if (styleExamples !== '[]') {
        appendPromptSection(sections, 'STYLE EXAMPLES', styleExamples);
    }
    appendPromptSection(sections, 'ADVANCED SYSTEM CONTEXT', values.systemContext);

    return sections.join('\n\n');
}

export function characterToFormValues(character: Character): CharacterFormValues {
    const triggerRules = parseTriggerRules(character.imageTriggerRules);
    return {
        aiName: character.aiName,
        avatarUrl: character.avatarUrl ?? '',
        appearanceReferenceUrl: character.appearanceReferenceUrl ?? '',
        appearanceReferenceObjectPath: character.appearanceReferenceObjectPath ?? '',
        posterUrl: character.posterUrl ?? '',
        visibility: character.visibility ?? 'private',
        role: character.role ?? '',
        character: character.character ?? '',
        personalityTraits: normalizeJsonField(character.personalityTraits, DEFAULT_PERSONALITY_TRAITS),
        biography: character.biography ?? '',
        speechStyle: character.speechStyle ?? '',
        relationshipContext: character.relationshipContext ?? '',
        memoryNotes: character.memoryNotes ?? '',
        responseBoundaries: character.responseBoundaries ?? '',
        systemContext: character.systemContext ?? '',
        rule: character.rule ?? '',
        styleExamples: character.styleExamples ?? '[]',
        imageEnabled: character.imageEnabled ?? true,
        photoKeywords: triggerRules.photoKeywords,
        activityKeywords: triggerRules.activityKeywords,
        imagePromptTemplate: character.imagePromptTemplate ?? '',
        personaFeedEnabled: character.personaFeedEnabled ?? false,
        personaFeedMinIntervalHours: character.personaFeedMinIntervalHours ?? 8,
        personaFeedMaxIntervalHours: character.personaFeedMaxIntervalHours ?? 24,
        personaFeedWindowStart: character.personaFeedWindowStart ?? '',
        personaFeedWindowEnd: character.personaFeedWindowEnd ?? '',
        personaFeedTimezone: character.personaFeedTimezone ?? DEFAULT_PERSONA_FEED_TIMEZONE,
    };
}

export function buildPayload(values: CharacterFormValues): CharacterPayload {
    return {
        aiName: values.aiName.trim(),
        avatarUrl: values.avatarUrl?.trim() || DEFAULT_AVATAR_URL,
        appearanceReferenceUrl: values.appearanceReferenceUrl?.trim() || '',
        appearanceReferenceObjectPath: values.appearanceReferenceUrl?.trim()
            ? values.appearanceReferenceObjectPath?.trim() || ''
            : '',
        posterUrl: values.posterUrl?.trim() || null,
        visibility: values.visibility,
        role: values.role?.trim() || null,
        character: values.character?.trim() || null,
        personalityTraits: normalizeJsonForPayload(values.personalityTraits, DEFAULT_PERSONALITY_TRAITS),
        biography: values.biography?.trim() || null,
        speechStyle: values.speechStyle?.trim() || null,
        relationshipContext: values.relationshipContext?.trim() || null,
        memoryNotes: values.memoryNotes?.trim() || null,
        responseBoundaries: values.responseBoundaries?.trim() || null,
        systemContext: values.systemContext?.trim() || null,
        rule: values.rule?.trim() || null,
        styleExamples: values.styleExamples?.trim() || '[]',
        imageEnabled: values.imageEnabled,
        imageTriggerRules: JSON.stringify({
            photoKeywords: splitKeywords(values.photoKeywords),
            activityKeywords: splitKeywords(values.activityKeywords),
        }),
        imagePromptTemplate: values.imagePromptTemplate?.trim() || null,
        personaFeedEnabled: values.visibility === 'public' && values.personaFeedEnabled,
        personaFeedMinIntervalHours: values.personaFeedMinIntervalHours,
        personaFeedMaxIntervalHours: values.personaFeedMaxIntervalHours,
        personaFeedWindowStart: values.personaFeedWindowStart?.trim() || null,
        personaFeedWindowEnd: values.personaFeedWindowEnd?.trim() || null,
        personaFeedTimezone: values.personaFeedTimezone?.trim() || DEFAULT_PERSONA_FEED_TIMEZONE,
    };
}
