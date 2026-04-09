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
}

export interface ChatResponse {
    reply: string;
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
    aiModel?: string | null;
    aiAvatarUrl?: string | null;
    createdAt?: string;
}
