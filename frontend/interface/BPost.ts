export interface UserSummary {
    id: number;
    supabaseUid?: string;
    displayName: string;
    avatarUrl?: string | null;
    lastSeenAt?: string | null;
}

export interface PostDto {
    id: number;
    author: UserSummary;
    content: string;
    imageUrls: string[];
    visibility: 'PUBLIC' | 'FRIENDS';
    createdAt: string;
    editedAt?: string | null;
    likeCount: number;
    commentCount: number;
    likedByMe: boolean;
}

export interface CommentDto {
    id: number;
    postId: number;
    author: UserSummary;
    content: string;
    createdAt: string;
    editedAt?: string | null;
}

export interface PageResponse<T> {
    items: T[];
    hasMore: boolean;
    nextCursor: number | null;
}

export type FriendshipStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED';

export interface FriendshipDto {
    id: number;
    requester: UserSummary;
    addressee: UserSummary;
    status: FriendshipStatus;
    createdAt: string;
    respondedAt?: string | null;
}

export interface ConversationDto {
    id: number;
    otherUser: UserSummary;
    lastMessageAt?: string | null;
    unreadCount: number;
    lastMessage?: MessageDto | null;
}

export interface MessageDto {
    id: number;
    conversationId: number;
    senderId: number;
    content?: string | null;
    imageUrl?: string | null;
    createdAt: string;
    readAt?: string | null;
}

export interface NotificationDto {
    id: number;
    type: 'POST_LIKE' | 'POST_COMMENT' | 'FRIEND_REQUEST' | 'FRIEND_ACCEPT' | 'MESSAGE';
    actor?: UserSummary | null;
    targetId?: number | null;
    targetKind?: string | null;
    message?: string | null;
    createdAt: string;
    readAt?: string | null;
}

export interface PresenceEvent {
    userId: number;
    online: boolean;
}
