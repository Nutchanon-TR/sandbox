import { ApiDetail } from "@/interface/common/ApiDetail";

const contextPath = process.env.NEXT_PUBLIC_API_URL || '';
// user-service base URL — dev สามารถ override เพื่อยิงไป prod gateway แทน local
const userServiceBase = process.env.NEXT_PUBLIC_USER_API_URL || contextPath;

export const API_SANDBOX: Record<string, ApiDetail> = {
    B_POST_UPLOAD_IMAGE: {
        path: `${contextPath}/v1/api/b-post/blog/upload-image`,
        method: 'POST',
    },
    CHAT_APP_MESSAGE: {
        path: `${contextPath}/v1/api/chat-app/chat`,
        method: 'POST',
    },
    CHAT_APP_HISTORY: {
        path: `${contextPath}/v1/api/chat-app/chat/history/{roomId}`,
        method: 'GET',
    },
    CHAT_APP_ROOM_LIST: {
        // userId removed from path: backend resolves caller from JWT (CurrentUser)
        path: `${contextPath}/v1/api/chat-app/room/list`,
        method: 'GET',
    },
    CHAT_APP_ROOM_CREATE: {
        path: `${contextPath}/v1/api/chat-app/room/create`,
        method: 'POST',
    },
    CHAT_APP_CHARACTER_LIST: {
        path: `${contextPath}/v1/api/chat-app/characters`,
        method: 'GET',
    },
    CHAT_APP_CHARACTER_CREATE: {
        path: `${contextPath}/v1/api/chat-app/characters`,
        method: 'POST',
    },
    CHAT_APP_CHARACTER_DETAIL: {
        path: `${contextPath}/v1/api/chat-app/characters/{characterId}`,
        method: 'GET',
    },
    CHAT_APP_CHARACTER_UPDATE: {
        path: `${contextPath}/v1/api/chat-app/characters/{characterId}`,
        method: 'PATCH',
    },
    CHAT_APP_CHARACTER_DELETE: {
        path: `${contextPath}/v1/api/chat-app/characters/{characterId}`,
        method: 'DELETE',
    },
    PERSONA_FEED_POSTS: {
        path: `${contextPath}/v1/api/chat-app/persona-feed/posts`,
        method: 'GET',
    },
    PERSONA_FEED_PROFILE: {
        path: `${contextPath}/v1/api/chat-app/persona-feed/personas/{personaId}`,
        method: 'GET',
    },
    PERSONA_FEED_PROFILE_POSTS: {
        path: `${contextPath}/v1/api/chat-app/persona-feed/personas/{personaId}/posts`,
        method: 'GET',
    },
    PERSONA_FEED_FOLLOW: {
        path: `${contextPath}/v1/api/chat-app/persona-feed/personas/{personaId}/follow`,
        method: 'POST',
    },
    PERSONA_FEED_DEV_PUBLISH_NOW: {
        path: `${contextPath}/v1/api/chat-app/persona-feed/dev/personas/{personaId}/publish-now`,
        method: 'POST',
    },
    USER_SYNC: {
        path: `${userServiceBase}/v1/api/user/sync`,
        method: 'POST',
    },
    USER_PROFILE_GET: {
        path: `${userServiceBase}/v1/api/user/profile/{supabaseUid}`,
        method: 'GET',
    },
    USER_PROFILE_CREATE: {
        path: `${userServiceBase}/v1/api/user/profile`,
        method: 'POST',
    },

    // ── B-Post: Posts ──
    B_POST_POST_CREATE: { path: `${contextPath}/v1/api/b-post/posts`, method: 'POST' },
    B_POST_POST_FEED: { path: `${contextPath}/v1/api/b-post/posts/feed`, method: 'GET' },
    B_POST_POST_BY_AUTHOR: { path: `${contextPath}/v1/api/b-post/posts/by-author/{authorId}`, method: 'GET' },
    B_POST_POST_GET: { path: `${contextPath}/v1/api/b-post/posts/{postId}`, method: 'GET' },
    B_POST_POST_UPDATE: { path: `${contextPath}/v1/api/b-post/posts/{postId}`, method: 'PATCH' },
    B_POST_POST_DELETE: { path: `${contextPath}/v1/api/b-post/posts/{postId}`, method: 'DELETE' },
    B_POST_POST_LIKE: { path: `${contextPath}/v1/api/b-post/posts/{postId}/likes`, method: 'POST' },
    B_POST_POST_UNLIKE: { path: `${contextPath}/v1/api/b-post/posts/{postId}/likes`, method: 'DELETE' },
    B_POST_POST_UPLOAD_IMAGE: { path: `${contextPath}/v1/api/b-post/posts/upload-image`, method: 'POST' },

    // ── B-Post: Comments ──
    B_POST_COMMENT_LIST: { path: `${contextPath}/v1/api/b-post/posts/{postId}/comments`, method: 'GET' },
    B_POST_COMMENT_ADD: { path: `${contextPath}/v1/api/b-post/posts/{postId}/comments`, method: 'POST' },
    B_POST_COMMENT_EDIT: { path: `${contextPath}/v1/api/b-post/comments/{commentId}`, method: 'PATCH' },
    B_POST_COMMENT_DELETE: { path: `${contextPath}/v1/api/b-post/comments/{commentId}`, method: 'DELETE' },

    // ── B-Post: Friends / Social ──
    B_POST_FRIEND_LIST: { path: `${contextPath}/v1/api/b-post/friends`, method: 'GET' },
    B_POST_FRIEND_REQUESTS_INCOMING: { path: `${contextPath}/v1/api/b-post/friends/requests/incoming`, method: 'GET' },
    B_POST_FRIEND_REQUESTS_OUTGOING: { path: `${contextPath}/v1/api/b-post/friends/requests/outgoing`, method: 'GET' },
    B_POST_FRIEND_REQUEST_SEND: { path: `${contextPath}/v1/api/b-post/friends/requests`, method: 'POST' },
    B_POST_FRIEND_REQUEST_ACCEPT: { path: `${contextPath}/v1/api/b-post/friends/requests/{id}/accept`, method: 'POST' },
    B_POST_FRIEND_REQUEST_DECLINE: { path: `${contextPath}/v1/api/b-post/friends/requests/{id}/decline`, method: 'POST' },
    B_POST_USER_SEARCH: { path: `${contextPath}/v1/api/b-post/users/search`, method: 'GET' },

    // ── B-Post: Messages ──
    B_POST_CONVERSATION_LIST: { path: `${contextPath}/v1/api/b-post/conversations`, method: 'GET' },
    B_POST_CONVERSATION_OPEN: { path: `${contextPath}/v1/api/b-post/conversations`, method: 'POST' },
    B_POST_CONVERSATION_HISTORY: { path: `${contextPath}/v1/api/b-post/conversations/{conversationId}/messages`, method: 'GET' },
    B_POST_CONVERSATION_READ: { path: `${contextPath}/v1/api/b-post/conversations/{conversationId}/read`, method: 'POST' },
    B_POST_MESSAGE_SEND: { path: `${contextPath}/v1/api/b-post/messages`, method: 'POST' },
    B_POST_MESSAGE_UPLOAD_IMAGE: { path: `${contextPath}/v1/api/b-post/messages/upload-image`, method: 'POST' },

    // ── B-Post: Notifications ──
    B_POST_NOTIFICATION_LIST: { path: `${contextPath}/v1/api/b-post/notifications`, method: 'GET' },
    B_POST_NOTIFICATION_UNREAD_COUNT: { path: `${contextPath}/v1/api/b-post/notifications/unread-count`, method: 'GET' },
    B_POST_NOTIFICATION_READ: { path: `${contextPath}/v1/api/b-post/notifications/{id}/read`, method: 'POST' },

    // ── B-Post: Presence ──
    B_POST_PRESENCE_ONLINE: { path: `${contextPath}/v1/api/b-post/presence/online`, method: 'GET' },

    // JOBJAB
    JOBJAB_PROFILE: { path: `${contextPath}/v1/api/jobjab/profile`, method: 'GET' },
    JOBJAB_PROFILE_SAVE: { path: `${contextPath}/v1/api/jobjab/profile`, method: 'PUT' },
    JOBJAB_SOURCE_LIST: { path: `${contextPath}/v1/api/jobjab/sources`, method: 'GET' },
    JOBJAB_SEARCH_RUN_CREATE: { path: `${contextPath}/v1/api/jobjab/search-runs`, method: 'POST' },
    JOBJAB_SEARCH_RUN_LIST: { path: `${contextPath}/v1/api/jobjab/search-runs`, method: 'GET' },
    JOBJAB_SEARCH_RUN_GET: { path: `${contextPath}/v1/api/jobjab/search-runs/{runId}`, method: 'GET' },
    JOBJAB_SEARCH_RUN_EVENTS: { path: `${contextPath}/v1/api/jobjab/search-runs/{runId}/events`, method: 'GET' },
    JOBJAB_JOB_LIST: { path: `${contextPath}/v1/api/jobjab/jobs`, method: 'GET' },
    JOBJAB_JOB_DETAIL: { path: `${contextPath}/v1/api/jobjab/jobs/{jobId}`, method: 'GET' },
    JOBJAB_JOB_MATCH: { path: `${contextPath}/v1/api/jobjab/jobs/{jobId}/match`, method: 'POST' },
    JOBJAB_TRACKING_LIST: { path: `${contextPath}/v1/api/jobjab/tracking`, method: 'GET' },
    JOBJAB_TRACKING_UPDATE: { path: `${contextPath}/v1/api/jobjab/jobs/{jobId}/tracking`, method: 'PUT' },
    JOBJAB_ROUTE_COMPUTE: { path: `${contextPath}/v1/api/jobjab/jobs/{jobId}/route`, method: 'POST' },
    JOBJAB_DIGEST_LIST: { path: `${contextPath}/v1/api/jobjab/digests`, method: 'GET' },
    JOBJAB_DIGEST_GENERATE_WEEKLY: { path: `${contextPath}/v1/api/jobjab/digests/weekly`, method: 'POST' },
};
