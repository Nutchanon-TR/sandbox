import { create } from 'zustand';
import { ConversationDto, MessageDto, NotificationDto } from '@/interface/BPost';

interface BPostState {
    // Realtime status
    connected: boolean;
    setConnected: (v: boolean) => void;

    // Online users
    online: Set<number>;
    setOnline: (ids: number[]) => void;
    onlineMark: (id: number, online: boolean) => void;

    // Notifications
    notifications: NotificationDto[];
    unreadCount: number;
    setNotifications: (list: NotificationDto[]) => void;
    pushNotification: (n: NotificationDto) => void;
    markNotificationRead: (id: number) => void;
    setUnreadCount: (n: number) => void;

    // Conversations
    conversations: ConversationDto[];
    setConversations: (list: ConversationDto[]) => void;
    upsertConversationLast: (msg: MessageDto, currentUserId: number) => void;
    clearUnread: (conversationId: number) => void;

    // Active conversation messages buffer (newest at end)
    activeConversationId: number | null;
    setActiveConversationId: (id: number | null) => void;
    messages: MessageDto[];
    setMessages: (list: MessageDto[]) => void;
    appendMessage: (m: MessageDto) => void;
    prependMessages: (list: MessageDto[]) => void;
}

export const useBPostStore = create<BPostState>((set, get) => ({
    connected: false,
    setConnected: (v) => set({ connected: v }),

    online: new Set(),
    setOnline: (ids) => set({ online: new Set(ids) }),
    onlineMark: (id, online) => {
        const next = new Set(get().online);
        if (online) next.add(id); else next.delete(id);
        set({ online: next });
    },

    notifications: [],
    unreadCount: 0,
    setNotifications: (list) =>
        set({ notifications: list, unreadCount: list.filter((n) => !n.readAt).length }),
    pushNotification: (n) =>
        set((s) => ({
            notifications: [n, ...s.notifications].slice(0, 50),
            unreadCount: s.unreadCount + (n.readAt ? 0 : 1),
        })),
    markNotificationRead: (id) =>
        set((s) => {
            const target = s.notifications.find((n) => n.id === id);
            const wasUnread = target && !target.readAt;
            return {
                notifications: s.notifications.map((n) =>
                    n.id === id ? { ...n, readAt: new Date().toISOString() } : n
                ),
                unreadCount: Math.max(0, s.unreadCount - (wasUnread ? 1 : 0)),
            };
        }),
    setUnreadCount: (n) => set({ unreadCount: n }),

    conversations: [],
    setConversations: (list) => set({ conversations: list }),
    upsertConversationLast: (msg, currentUserId) =>
        set((s) => ({
            conversations: s.conversations.map((c) =>
                c.id === msg.conversationId
                    ? {
                          ...c,
                          lastMessage: msg,
                          lastMessageAt: msg.createdAt,
                          unreadCount:
                              msg.senderId !== currentUserId && get().activeConversationId !== msg.conversationId
                                  ? c.unreadCount + 1
                                  : c.unreadCount,
                      }
                    : c
            ),
        })),
    clearUnread: (conversationId) =>
        set((s) => ({
            conversations: s.conversations.map((c) =>
                c.id === conversationId ? { ...c, unreadCount: 0 } : c
            ),
        })),

    activeConversationId: null,
    setActiveConversationId: (id) => set({ activeConversationId: id }),
    messages: [],
    setMessages: (list) => set({ messages: list }),
    appendMessage: (m) =>
        set((s) =>
            s.activeConversationId === m.conversationId &&
            !s.messages.some((existing) => existing.id === m.id)
                ? { messages: [...s.messages, m] }
                : s
        ),
    prependMessages: (list) =>
        set((s) => ({ messages: [...list, ...s.messages] })),
}));
