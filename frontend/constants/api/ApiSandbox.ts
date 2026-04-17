import { ApiDetail } from "@/interface/common/ApiDetail";

const contextPath = process.env.NEXT_PUBLIC_API_URL || '';

export const API_SANDBOX: Record<string, ApiDetail> = {
    B_POST_UPLOAD_IMAGE: {
        path: `${contextPath}/v1/api/b-post/blog/upload-image`,
        method: 'POST',
    },
    DINNER_SUPPLIER_ORDER: {
        path: `${contextPath}/v1/api/dinner/supplier/inquiry`,
        method: 'GET',
    },
    CHAT_APP_MESSAGE: {
        path: `${contextPath}/v1/api/chat-app/message`,
        method: 'POST',
    },
    CHAT_APP_HISTORY: {
        path: `${contextPath}/v1/api/chat-app/message/history/{roomId}`,
        method: 'GET',
    },
    CHAT_APP_ROOM_LIST: {
        path: `${contextPath}/v1/api/chat-app/room/list/{userId}`,
        method: 'GET',
    },
    USER_RESOLVE: {
        path: `${contextPath}/v1/api/user/user/resolve`,
        method: 'POST',
    },
    USER_PROFILE_GET: {
        path: `${contextPath}/v1/api/user/profile/{supabaseUid}`,
        method: 'GET',
    },
    USER_PROFILE_CREATE: {
        path: `${contextPath}/v1/api/user/profile`,
        method: 'POST',
    },
};
