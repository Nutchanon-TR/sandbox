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
}

export interface RoomCreateResponse {
    id: number;
    name: string;
    isGroup: boolean;
    aiContextId?: number | null;
    aiAvatarUrl?: string | null;
    createdAt?: string;
}
