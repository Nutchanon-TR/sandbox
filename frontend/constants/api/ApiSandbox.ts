import { ApiDetail } from "@/interface/common/ApiDetail";

const contextPath = process.env.NEXT_PUBLIC_API_URL || '';
// user-service base URL — dev สามารถ override เพื่อยิงไป prod gateway แทน local
const userServiceBase = process.env.NEXT_PUBLIC_USER_API_URL || contextPath;

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
        path: `${contextPath}/v1/api/chat-app/chat`,
        method: 'POST',
    },
    CHAT_APP_HISTORY: {
        path: `${contextPath}/v1/api/chat-app/chat/history/{roomId}`,
        method: 'GET',
    },
    CHAT_APP_ROOM_LIST: {
        path: `${contextPath}/v1/api/chat-app/room/list/{userId}`,
        method: 'GET',
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
};
