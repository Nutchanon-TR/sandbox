'use client';

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Avatar, Badge, Button, Empty, Input, Space, Spin, Typography } from 'antd';
import Image from 'next/image';
import {
    MoreOutlined,
    PhoneOutlined,
    RobotOutlined,
    SendOutlined,
    TeamOutlined,
    VideoCameraOutlined,
} from "@ant-design/icons";
import { TITLE } from "@/constants/Title";
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { useNotification } from '@/providers/NotificationProvider';
import { useSessionStore } from '@/stores/sessionStore';
import {
    ChatMessage,
    ChatResponse,
    MessageHistoryResponse,
    RoomSummary,
    UserResolveResponse,
} from '@/interface/ChatApp';
import { fetchApi } from '@/utils/api';
import { useChangeTitle } from "@/utils/breadCrumbUtil";
import { useChangeSubSideBar } from '@/utils/subSideBarUtil';
import { usePathname, useSearchParams } from 'next/navigation';

const { Text } = Typography;

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

// --- Style constants ---

const BORDER = {
    main: 'border-slate-300 dark:border-border-main',
    secondary: 'border-slate-300 dark:border-border-secondary',
    input: '!border-slate-300 dark:!border-border-secondary hover:!border-slate-400 dark:hover:!border-border-main',
} as const;

// --- Component ---

export default function MessagePage() {
    // ── Auth & routing ──
    const session = useSessionStore((s) => s.session);
    const status = useSessionStore((s) => s.status);
    const pathname = usePathname();
    const searchParams = useSearchParams();
    const notification = useNotification();

    // ── State ──
    const [rooms, setRooms] = useState<RoomSummary[]>([]);
    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [inputText, setInputText] = useState('');

    const [currentUserId, setCurrentUserId] = useState<number | null>(null);
    const [resolvedRoomId, setResolvedRoomId] = useState<number | null>(null);
    const [selectedRoomId, setSelectedRoomId] = useState<number | null>(null);

    const [isResolving, setIsResolving] = useState(true);
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

    // ── Step 1: Resolve user from Supabase session ──
    useEffect(() => {
        if (status === 'loading') return;
        if (status === 'unauthenticated' || !session?.user) {
            setIsResolving(false);
            return;
        }

        const resolveUser = async () => {
            try {
                const response = await fetchApi<UserResolveResponse>(
                    API_SANDBOX.USER_RESOLVE,
                    {
                        supabaseUid: session.user.id,
                        email: session.user.email || '',
                        username: session.user.user_metadata?.full_name
                            || session.user.email?.split('@')[0]
                            || 'user',
                    }
                );
                console.log('[ChatApp] Resolved user — userId:', response.userId);
                setCurrentUserId(response.userId);
                setResolvedRoomId(response.roomId ?? null);
            } catch (error: unknown) {
                console.error('[ChatApp] Failed to resolve user:', error);
                notification.error({
                    message: 'Error',
                    description: getErrorMessage(error, 'Failed to resolve user session'),
                });
            } finally {
                setIsResolving(false);
            }
        };

        void resolveUser();
    }, [session, status, notification]);

    // ── Step 2: Fetch room list ──
    const fetchRooms = useCallback(async () => {
        if (currentUserId === null) return;

        setIsRoomsLoading(true);
        try {
            const response = await fetchApi<RoomSummary[]>(
                API_SANDBOX.CHAT_APP_ROOM_LIST,
                {},
                { userId: currentUserId }
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

    // ── Step 3: Fetch chat history when room changes ──
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
        loading: isRoomsLoading || isResolving,
        onSelect: handleSelectRoom,
    }), [activeRoomId, handleSelectRoom, isResolving, isRoomsLoading, roomItems]);

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

    // ── Send message ──
    const handleSendMessage = async () => {
        if (!inputText.trim() || isSending || activeRoomId === null || currentUserId === null) return;

        const outgoingMessage: ChatMessage = {
            content: inputText,
            role: 'USER',
            senderId: currentUserId,
            roomId: activeRoomId,
        };

        setMessages((prev) => [...prev, outgoingMessage]);
        setInputText('');
        setIsSending(true);

        try {
            const response = await fetchApi<ChatResponse>(API_SANDBOX.CHAT_APP_MESSAGE, {
                roomId: activeRoomId,
                senderId: currentUserId,
                message: outgoingMessage.content,
            });

            const aiMessage: ChatMessage = { content: response.reply, role: 'AI' };
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
    if (isResolving || status === 'loading') {
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

    if (currentUserId === null) {
        return (
            <div className="flex h-full items-center justify-center text-slate-500">
                <p>Chat service is currently unavailable. Please try again later.</p>
            </div>
        );
    }

    // ── Render ──
    const roomSubtitle = getRoomSubtitle(selectedRoom);
    const aiAvatarSrc = selectedRoom?.aiAvatarUrl || '/ai_avatar.png';

    return (
        <div className={`flex h-full min-h-0 flex-1 overflow-hidden rounded-[28px] border bg-background shadow-sm ${BORDER.main}`}>
            <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
                {isRoomsLoading ? (
                    <div className="flex flex-1 items-center justify-center px-6">
                        <Spin size="large" />
                    </div>
                ) : activeRoomId === null || selectedRoom === null ? (
                    <div className="flex flex-1 items-center justify-center px-6">
                        <Empty description="No rooms available" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    </div>
                ) : (
                    <>
                        {/* Header */}
                        <div className={`sticky top-0 z-20 flex items-center justify-between border-b bg-surface px-5 py-4 backdrop-blur supports-[backdrop-filter]:bg-surface/80 ${BORDER.main}`}>
                            <Space size="middle">
                                <Badge dot color="green" offset={[-5, 35]}>
                                    <Avatar
                                        src={aiAvatarSrc}
                                        size={42}
                                        className={`border bg-muted ${BORDER.secondary}`}
                                    />
                                </Badge>
                                <div className="flex flex-col">
                                    <Text strong className="text-lg leading-none !text-foreground">
                                        {selectedRoom.name}
                                    </Text>
                                    {roomSubtitle && (
                                        <Text type="secondary" className="mt-1 text-xs font-medium">
                                            {roomSubtitle}
                                        </Text>
                                    )}
                                </div>
                            </Space>
                            <Space size="small">
                                <Button type="text" className="!text-text-secondary hover:!bg-muted" icon={<PhoneOutlined />} />
                                <Button type="text" className="!text-text-secondary hover:!bg-muted" icon={<VideoCameraOutlined />} />
                                <Button type="text" className="!text-text-secondary hover:!bg-muted" icon={<MoreOutlined />} />
                            </Space>
                        </div>

                        {/* Messages area */}
                        <div
                            ref={scrollContainerRef}
                            onScroll={handleScroll}
                            className="flex-1 overflow-y-auto bg-background px-4 py-5 md:px-6"
                        >
                            {isHistoryLoading ? (
                                <div className="flex h-full items-center justify-center">
                                    <Spin size="large" />
                                </div>
                            ) : (
                                <>
                                    {isLoadingMore && (
                                        <div className="flex justify-center py-3">
                                            <Spin size="small" />
                                        </div>
                                    )}

                                    <div className={`flex min-h-full flex-col gap-4 ${messages.length === 0 && !isSending ? 'items-center justify-center' : 'justify-end'}`}>
                                        {messages.length === 0 && !isSending && (
                                            <div className="flex max-w-md flex-col items-center text-center text-text-secondary">
                                                <Image
                                                    src={aiAvatarSrc}
                                                    alt="AI Avatar"
                                                    width={80}
                                                    height={80}
                                                    className="mb-4 opacity-45 dark:opacity-60"
                                                />
                                                <p className="m-0">Say hello to start the conversation!</p>
                                            </div>
                                        )}

                                        {messages.map((message, index) => {
                                            const ai = isAiMessage(message);

                                            return (
                                                <div
                                                    key={message.id ?? `msg-${index}`}
                                                    className={`flex flex-col ${ai ? 'items-start' : 'items-end'}`}
                                                >
                                                    <div className="flex max-w-[85%] items-end gap-2 md:max-w-[72%]">
                                                        {ai && (
                                                            <Avatar
                                                                src={aiAvatarSrc}
                                                                size={32}
                                                                className={`shrink-0 border bg-muted ${BORDER.secondary}`}
                                                            />
                                                        )}
                                                        <div
                                                            className={`rounded-3xl p-3 shadow-sm ${ai
                                                                ? `rounded-bl-md border bg-surface text-foreground ${BORDER.main}`
                                                                : 'rounded-br-md bg-blue-600 text-white dark:bg-blue-500'
                                                                }`}
                                                        >
                                                            <p className="m-0 whitespace-pre-wrap break-words text-sm leading-relaxed">
                                                                {message.content}
                                                            </p>
                                                        </div>
                                                    </div>
                                                </div>
                                            );
                                        })}

                                        {isSending && (
                                            <div className="flex justify-start">
                                                <div className="flex max-w-[70%] items-end gap-2">
                                                    <Avatar
                                                        src={aiAvatarSrc}
                                                        size={32}
                                                        className={`border bg-muted ${BORDER.secondary}`}
                                                    />
                                                    <div className={`flex items-center gap-1 rounded-3xl rounded-bl-md border bg-surface p-4 shadow-sm ${BORDER.main}`}>
                                                        <div className="h-2 w-2 animate-bounce rounded-full bg-text-secondary/60 [animation-delay:-0.3s]" />
                                                        <div className="h-2 w-2 animate-bounce rounded-full bg-text-secondary/60 [animation-delay:-0.15s]" />
                                                        <div className="h-2 w-2 animate-bounce rounded-full bg-text-secondary/60" />
                                                    </div>
                                                </div>
                                            </div>
                                        )}

                                        <div ref={messagesEndRef} />
                                    </div>
                                </>
                            )}
                        </div>

                        {/* Input bar */}
                        <div className={`sticky bottom-0 z-20 border-t bg-surface px-4 py-4 backdrop-blur supports-[backdrop-filter]:bg-surface/85 md:px-6 ${BORDER.main}`}>
                            <div className="mx-auto flex w-full max-w-4xl items-center gap-2">
                                <Input
                                    size="large"
                                    value={inputText}
                                    onChange={(event) => setInputText(event.target.value)}
                                    onPressEnter={handleSendMessage}
                                    placeholder="Type a message..."
                                    disabled={isSending || isHistoryLoading}
                                    className={`rounded-full !bg-muted px-5 !text-foreground placeholder:!text-text-secondary focus:!border-accent ${BORDER.input}`}
                                />
                                <Button
                                    type="primary"
                                    shape="circle"
                                    size="large"
                                    icon={<SendOutlined />}
                                    onClick={handleSendMessage}
                                    disabled={!inputText.trim() || isSending || isHistoryLoading}
                                    className="flex items-center justify-center"
                                />
                            </div>
                        </div>
                    </>
                )}
            </div>
        </div>
    );
}
