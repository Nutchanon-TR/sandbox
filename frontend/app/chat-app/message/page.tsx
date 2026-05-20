'use client';

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Empty, Spin } from 'antd';
import { RobotOutlined, TeamOutlined } from "@ant-design/icons";
import { TITLE } from "@/constants/Title";
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { CHAT_BORDER, ChatHeader, MessageInputBar, MessagesArea } from '@/components/ChatApp';
import { useNotification } from '@/providers/NotificationProvider';
import { useSessionStore } from '@/stores/sessionStore';
import {
    ChatMessage,
    ChatResponse,
    MessageHistoryResponse,
    RoomSummary,
} from '@/interface/ChatApp';
import { fetchApi } from '@/utils/api';
import { useChangeTitle } from "@/utils/breadCrumbUtil";
import { useChangeSubSideBar } from '@/utils/subSideBarUtil';
import { usePathname, useSearchParams } from 'next/navigation';

// --- Helpers ---

function getErrorMessage(error: unknown, fallbackMessage: string): string {
    if (typeof error === "object" && error !== null && "response" in error) {
        const response = (error as { response?: { data?: { message?: unknown } } }).response;
        if (typeof response?.data?.message === "string") {
            return response.data.message;
        }
    }
    if (error instanceof Error) return error.message;
    return fallbackMessage;
}

function isAiMessage(message: ChatMessage): boolean {
    return message.isAi === true || message.senderRole?.toUpperCase() === 'AI' || message.role === 'AI';
}

function normalizeMessage(message: ChatMessage): ChatMessage {
    return { ...message, role: isAiMessage(message) ? 'AI' : 'USER' };
}

function getRoomSubtitle(room: RoomSummary | null): string | undefined {
    if (!room) return undefined;
    if (room.isGroup) return 'Group chat';
    if (room.aiModel) return `AI model: ${room.aiModel}`;
    return undefined;
}

// --- Component ---

export default function MessagePage() {
    // ── Auth & routing ──
    const status = useSessionStore((s) => s.status);
    const currentUserId = useSessionStore((s) => s.internalUserId);
    const pathname = usePathname();
    const searchParams = useSearchParams();
    const notification = useNotification();

    // ── State ──
    const [rooms, setRooms] = useState<RoomSummary[]>([]);
    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [inputText, setInputText] = useState('');

    const [resolvedRoomId, setResolvedRoomId] = useState<number | null>(null);
    const [selectedRoomId, setSelectedRoomId] = useState<number | null>(null);

    const [isRoomsLoading, setIsRoomsLoading] = useState(false);
    const [isHistoryLoading, setIsHistoryLoading] = useState(false);
    const [isSending, setIsSending] = useState(false);
    const [isLoadingMore, setIsLoadingMore] = useState(false);

    const [hasMore, setHasMore] = useState(false);
    const [oldestMessageId, setOldestMessageId] = useState<number | null>(null);

    // ── Refs ──
    const messagesEndRef = useRef<HTMLDivElement>(null);
    const scrollContainerRef = useRef<HTMLDivElement>(null);

    // ── Derive initial room from URL query ──
    const roomIdFromQuery = searchParams.get("roomId");
    const initialRoomId = roomIdFromQuery && !Number.isNaN(Number(roomIdFromQuery))
        ? Number(roomIdFromQuery)
        : null;

    // ── Active room: selectedRoomId (user click) > URL query > resolvedRoomId ──
    const activeRoomId = selectedRoomId ?? initialRoomId ?? resolvedRoomId;
    const selectedRoom = rooms.find((room) => room.id === activeRoomId) ?? null;

    // ── Title ──
    useChangeTitle(TITLE.CHAT_APP, "MESSAGE");

    // ── Step 1: Fetch room list ──
    const fetchRooms = useCallback(async () => {
        if (currentUserId === null) return;

        setIsRoomsLoading(true);
        try {
            const response = await fetchApi<RoomSummary[]>(
                API_SANDBOX.CHAT_APP_ROOM_LIST,
                {},
            );
            setRooms(response);

            if (response.length === 0) {
                setMessages([]);
                setHasMore(false);
                setOldestMessageId(null);
            }
        } catch (error: unknown) {
            console.error('[ChatApp] Failed to fetch rooms:', error);
            notification.error({
                message: 'Error',
                description: getErrorMessage(error, 'Failed to fetch room list'),
            });
        } finally {
            setIsRoomsLoading(false);
        }
    }, [currentUserId, notification]);

    useEffect(() => { void fetchRooms(); }, [fetchRooms]);

    // ── Step 2: Fetch chat history when room changes ──
/*  */    useEffect(() => {
        if (activeRoomId == null) {
            setMessages([]);
            setHasMore(false);
            setOldestMessageId(null);
            return;
        }

        let ignore = false;
        setIsHistoryLoading(true);

        fetchApi<MessageHistoryResponse>(
            API_SANDBOX.CHAT_APP_HISTORY,
            { limit: 20 },
            { roomId: activeRoomId }
        )
            .then((response) => {
                if (ignore) return;
                const history = response.messages.map(normalizeMessage);
                setMessages(history);
                setHasMore(response.hasMore);
                setOldestMessageId(history.length > 0 ? (history[0].id ?? null) : null);
            })
            .catch((error: unknown) => {
                if (ignore) return;
                console.error('[ChatApp] Failed to fetch chat history:', error);
                notification.error({
                    message: 'Error',
                    description: getErrorMessage(error, 'Failed to fetch chat history'),
                });
            })
            .finally(() => {
                if (!ignore) setIsHistoryLoading(false);
            });

        return () => { ignore = true; };
    }, [activeRoomId, notification]);

    // ── Auto-scroll to bottom on new messages ──
    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages, isSending, isHistoryLoading]);

    // ── SubSideBar config ──
    const roomItems = useMemo(() => rooms.map((room) => ({
        key: room.id,
        label: room.name,
        description: room.isGroup ? 'Group room' : room.aiModel || undefined,
        icon: room.isGroup ? <TeamOutlined /> : <RobotOutlined />,
    })), [rooms]);

    const handleSelectRoom = useCallback((key: string | number) => {
        const roomId = Number(key);
        setSelectedRoomId(roomId);

        const nextParams = new URLSearchParams(searchParams.toString());
        nextParams.set("roomId", String(key));
        window.history.replaceState(null, "", `${pathname}?${nextParams.toString()}`);

        setMessages([]);
        setHasMore(false);
        setOldestMessageId(null);
    }, [pathname, searchParams]);

    const subSideBarConfig = useMemo(() => ({
        title: "Rooms",
        items: roomItems,
        selectedKey: activeRoomId ?? undefined,
        emptyText: "No rooms available",
        loading: currentUserId === null || isRoomsLoading,
        onSelect: handleSelectRoom,
    }), [activeRoomId, currentUserId, handleSelectRoom, isRoomsLoading, roomItems]);

    useChangeSubSideBar(subSideBarConfig);

    // ── Load older messages (infinite scroll up) ──
    const loadMoreMessages = useCallback(async () => {
        if (activeRoomId === null || !hasMore || isLoadingMore || oldestMessageId === null) return;

        setIsLoadingMore(true);
        const container = scrollContainerRef.current;
        const previousScrollHeight = container?.scrollHeight ?? 0;

        try {
            const response = await fetchApi<MessageHistoryResponse>(
                API_SANDBOX.CHAT_APP_HISTORY,
                { beforeId: oldestMessageId, limit: 20 },
                { roomId: activeRoomId }
            );
            const olderMessages = response.messages.map(normalizeMessage);

            setMessages((prev) => [...olderMessages, ...prev]);
            setHasMore(response.hasMore);
            setOldestMessageId(olderMessages.length > 0 ? (olderMessages[0].id ?? null) : null);

            requestAnimationFrame(() => {
                if (container) {
                    container.scrollTop = container.scrollHeight - previousScrollHeight;
                }
            });
        } catch (error: unknown) {
            console.error('[ChatApp] Failed to load more messages:', error);
            notification.error({
                message: 'Error',
                description: getErrorMessage(error, 'Failed to load more messages'),
            });
        } finally {
            setIsLoadingMore(false);
        }
    }, [activeRoomId, hasMore, isLoadingMore, oldestMessageId, notification]);

    const handleScroll = useCallback((event: React.UIEvent<HTMLDivElement>) => {
        if (event.currentTarget.scrollTop === 0 && hasMore && !isLoadingMore) {
            void loadMoreMessages();
        }
    }, [hasMore, isLoadingMore, loadMoreMessages]);

    const handleFixedChromeWheel = useCallback((event: React.WheelEvent<HTMLDivElement>) => {
        const container = scrollContainerRef.current;
        if (!container) return;

        container.scrollTop += event.deltaY;
        event.preventDefault();
    }, []);

    // ── Send message ──
    const handleSendMessage = async () => {
        if (!inputText.trim() || isSending || activeRoomId === null || currentUserId === null) return;

        const outgoingMessage: ChatMessage = {
            content: inputText,
            role: 'USER',
            senderId: currentUserId,
            roomId: activeRoomId,
            attachments: [],
        };

        setMessages((prev) => [...prev, outgoingMessage]);
        setInputText('');
        setIsSending(true);

        try {
            const response = await fetchApi<ChatResponse>(API_SANDBOX.CHAT_APP_MESSAGE, {
                roomId: activeRoomId,
                message: outgoingMessage.content,
                // senderId removed: backend resolves caller from JWT (Authorization header)
            });

            const aiMessage: ChatMessage = {
                content: response.reply,
                role: 'AI',
                isAi: true,
                roomId: activeRoomId,
                attachments: response.attachments ?? [],
            };
            setMessages((prev) => [...prev, aiMessage]);
        } catch (error: unknown) {
            console.error('[ChatApp] Failed to send message:', error);
            notification.error({
                message: 'Error',
                description: getErrorMessage(error, 'Failed to send message'),
            });
        } finally {
            setIsSending(false);
        }
    };

    // ── Guards ──
    if (status === 'loading') {
        return (
            <div className="flex h-full items-center justify-center">
                <Spin size="large" />
            </div>
        );
    }

    if (status === 'unauthenticated') {
        return (
            <div className="flex h-full items-center justify-center text-slate-500">
                <p>Please log in to use the chat.</p>
            </div>
        );
    }

    // ── Render ──
    const isSyncing = currentUserId === null;
    const roomSubtitle = getRoomSubtitle(selectedRoom);
    const aiAvatarSrc = selectedRoom?.aiAvatarUrl || '/ai_avatar.png';

    return (
        <div className={`flex h-full min-h-0 max-h-full min-w-0 flex-1 overflow-hidden rounded-[28px] border bg-background shadow-sm ${CHAT_BORDER.main}`}>
            <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
                {isSyncing || isRoomsLoading ? (
                    <div className="flex flex-1 items-center justify-center px-6">
                        <Spin size="large" />
                    </div>
                ) : activeRoomId === null || selectedRoom === null ? (
                    <div className="flex flex-1 items-center justify-center px-6">
                        <Empty description="No rooms available" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    </div>
                ) : (
                    <>
                        <ChatHeader
                            room={selectedRoom}
                            subtitle={roomSubtitle}
                            avatarSrc={aiAvatarSrc}
                            onWheel={handleFixedChromeWheel}
                        />
                        <MessagesArea
                            messages={messages}
                            avatarSrc={aiAvatarSrc}
                            isHistoryLoading={isHistoryLoading}
                            isLoadingMore={isLoadingMore}
                            isSending={isSending}
                            scrollContainerRef={scrollContainerRef}
                            messagesEndRef={messagesEndRef}
                            onScroll={handleScroll}
                            isAiMessage={isAiMessage}
                        />
                        <MessageInputBar
                            value={inputText}
                            disabled={isSending || isHistoryLoading}
                            isSending={isSending}
                            onChange={setInputText}
                            onSend={handleSendMessage}
                            onWheel={handleFixedChromeWheel}
                        />
                    </>
                )}
            </div>
        </div>
    );
}
