export interface ChatAttachment {
    id?: number;
    type: 'image' | string;
    url: string;
    mimeType?: string | null;
    prompt?: string | null;
    provider?: string | null;
    metadata?: string | null;
    createdAt?: string;
}

export interface ChatMessage {
    id?: number;
    roomId?: number;
    senderId?: number;
    senderUsername?: string;
    senderRole?: string;
    isAi?: boolean;
    content: string;
    createdAt?: string;
    role?: 'AI' | 'USER';
    attachments?: ChatAttachment[];
}

export interface ChatResponse {
    reply: string;
    attachments?: ChatAttachment[];
}

export interface MessageHistoryResponse {
    messages: ChatMessage[];
    hasMore: boolean;
}

export interface UserResolveResponse {
    userId: number;
    roomId: number;
}

export interface RoomSummary {
    id: number;
    name: string;
    isGroup: boolean;
    aiContextId?: number | null;
    aiModel?: string | null;
    aiAvatarUrl?: string | null;
    createdAt?: string;
}

export interface Character {
    id: number;
    createdByUserId?: number | null;
    aiName: string;
    avatarUrl?: string | null;
    appearanceReferenceUrl?: string | null;
    appearanceReferenceObjectPath?: string | null;
    role?: string | null;
    character?: string | null;
    biography?: string | null;
    rule?: string | null;
    posterUrl?: string | null;
    visibility?: string | null;
    styleExamples?: string | null;
    imageEnabled?: boolean;
    imageTriggerRules?: string | null;
    imagePromptTemplate?: string | null;
    fineTuneStatus?: string | null;
    fineTunedModelId?: string | null;
    personaFeedEnabled?: boolean;
    personaFeedMinIntervalHours?: number;
    personaFeedMaxIntervalHours?: number;
    personaFeedWindowStart?: string | null;
    personaFeedWindowEnd?: string | null;
    personaFeedTimezone?: string | null;
}

export interface RoomCreateResponse {
    id: number;
    name: string;
    isGroup: boolean;
    aiContextId?: number | null;
    aiAvatarUrl?: string | null;
    createdAt?: string;
}

export interface CursorPageResponse<T> {
    items: T[];
    hasMore: boolean;
    nextCursor: number | null;
}

export interface PersonaFeedPersonaSummary {
    id: number;
    aiName: string;
    avatarUrl?: string | null;
    posterUrl?: string | null;
}

export interface PersonaFeedPost {
    id: number;
    persona: PersonaFeedPersonaSummary;
    content: string;
    imageUrls: string[];
    createdAt: string;
    followedByMe: boolean;
}

export interface PersonaFeedPersonaProfile extends PersonaFeedPersonaSummary {
    role?: string | null;
    biography?: string | null;
    followedByMe: boolean;
}

export interface PersonaFeedFollowResponse {
    followed: boolean;
    roomId: number;
}
