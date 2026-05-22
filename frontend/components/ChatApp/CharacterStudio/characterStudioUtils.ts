import type { Character } from '@/interface/ChatApp';
import type { CharacterFormValues, CharacterPayload } from './types';

export const DEFAULT_PHOTO_KEYWORDS = 'ถ่ายรูป, ส่งรูป, ขอดูรูป, ถ่ายมาให้ดู';
export const DEFAULT_ACTIVITY_KEYWORDS = 'ทำอะไรอยู่, ตอนนี้ทำไร, อยู่ไหน, ทำอะไรตอนนี้';
export const DEFAULT_AVATAR_URL = '/ai_avatar.png';
export const DEFAULT_PERSONA_FEED_TIMEZONE = 'Asia/Bangkok';

export const DEFAULT_FORM_VALUES: CharacterFormValues = {
    aiName: '',
    avatarUrl: '',
    posterUrl: '',
    visibility: 'private',
    role: '',
    character: '',
    biography: '',
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

export function characterToFormValues(character: Character): CharacterFormValues {
    const triggerRules = parseTriggerRules(character.imageTriggerRules);
    return {
        aiName: character.aiName,
        avatarUrl: character.avatarUrl ?? '',
        posterUrl: character.posterUrl ?? '',
        visibility: character.visibility ?? 'private',
        role: character.role ?? '',
        character: character.character ?? '',
        biography: character.biography ?? '',
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
        posterUrl: values.posterUrl?.trim() || null,
        visibility: values.visibility,
        role: values.role?.trim() || null,
        character: values.character?.trim() || null,
        biography: values.biography?.trim() || null,
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
