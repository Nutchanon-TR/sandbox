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
import { useNotification } from '@/context/NotificationContext';
import { useSupabaseSession } from '@/hooks/useSupabaseSession';
import { fetchApi } from '@/utils/api';
import { useChangeTitle } from "@/utils/breadCrumbUtil";
import { useChangeSubSideBar } from '@/utils/subSideBarUtil';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';

const { Text } = Typography;

interface Message {
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

interface ChatResponse {
    reply: string;
}

interface MessageHistoryResponse {
    messages: Message[];
    hasMore: boolean;
}

interface UserResolveResponse {
    userId: number;
    roomId: number;
}

interface RoomSummary {
    id: number;
    name: string;
    isGroup: boolean;
    aiModel?: string | null;
    createdAt?: string;
}

function getErrorMessage(error: unknown, fallbackMessage: string) {
    if (typeof error === "object" && error !== null && "response" in error) {
        const response = (error as { response?: { data?: { message?: unknown } } }).response;
        if (typeof response?.data?.message === "string") {
            return response.data.message;
        }
    }

    if (error instanceof Error) {
        return error.message;
    }

    return fallbackMessage;
}

function isAiMessage(message: Message) {
    return message.isAi === true || message.senderRole?.toUpperCase() === 'AI' || message.role === 'AI';
}

function normalizeMessage(message: Message): Message {
    return {
        ...message,
        role: isAiMessage(message) ? 'AI' : 'USER',
    };
}

export default function MessagePage() {
    const [rooms, setRooms] = useState<RoomSummary[]>([]);
    const [messages, setMessages] = useState<Message[]>([]);
    const [inputText, setInputText] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [isHistoryLoading, setIsHistoryLoading] = useState(false);
    const [isRoomsLoading, setIsRoomsLoading] = useState(false);
    const [isResolving, setIsResolving] = useState(true);
    const [resolvedRoomId, setResolvedRoomId] = useState<number | null>(null);
    const [currentUserId, setCurrentUserId] = useState<number | null>(null);
    const [hasMore, setHasMore] = useState(false);
    const [isLoadingMore, setIsLoadingMore] = useState(false);
    const [oldestMessageId, setOldestMessageId] = useState<number | null>(null);
    const notification = useNotification();
    const messagesEndRef = useRef<HTMLDivElement>(null);
    const scrollContainerRef = useRef<HTMLDivElement>(null);
    const { data: session, status } = useSupabaseSession();
    const pathname = usePathname();
    const router = useRouter();
    const searchParams = useSearchParams();

    useChangeTitle(TITLE.CHAT_APP, "MESSAGE");

    useEffect(() => {
        if (status === 'loading') return;
        if (status === 'unauthenticated' || !session?.user) {
            setIsResolving(false);
            return;
        }

        const resolveUser = async () => {
            try {
                const response = await fetchApi<UserResolveResponse>(
                    API_SANDBOX.CHAT_APP_RESOLVE_USER,
                    {
                        supabaseUid: session.user.id,
                        email: session.user.email || '',
                        username: session.user.user_metadata?.full_name
                            || session.user.email?.split('@')[0]
                            || 'user',
                    }
                );
                setCurrentUserId(response.userId);
                setResolvedRoomId(response.roomId);
            } catch (error: unknown) {
                console.error('Failed to resolve user:', error);
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
            console.error('Failed to fetch rooms:', error);
            notification.error({
                message: 'Error',
                description: getErrorMessage(error, 'Failed to fetch room list'),
            });
        } finally {
            setIsRoomsLoading(false);
        }
    }, [currentUserId, notification]);

    useEffect(() => {
        void fetchRooms();
    }, [fetchRooms]);

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages, isLoading, isHistoryLoading]);

    const roomIdFromQuery = searchParams.get("roomId");
    const activeRoomId = roomIdFromQuery && !Number.isNaN(Number(roomIdFromQuery))
        ? Number(roomIdFromQuery)
        : resolvedRoomId;
    const selectedRoom = rooms.find((room) => room.id === activeRoomId) ?? null;
    const roomItems = useMemo(() => rooms.map((room) => ({
        key: room.id,
        label: room.name,
        description: room.isGroup ? 'Group room' : room.aiModel || undefined,
        icon: room.isGroup ? <TeamOutlined /> : <RobotOutlined />,
    })), [rooms]);

    const handleSelectRoom = useCallback((key: string | number) => {
        const nextParams = new URLSearchParams(searchParams.toString());
        nextParams.set("roomId", String(key));
        router.replace(`${pathname}?${nextParams.toString()}`);

        setMessages([]);
        setHasMore(false);
        setOldestMessageId(null);
    }, [pathname, router, searchParams]);

    const subSideBarConfig = useMemo(() => ({
        title: "Rooms",
        items: roomItems,
        selectedKey: activeRoomId ?? undefined,
        emptyText: "No rooms available",
        loading: isRoomsLoading || isResolving,
        onSelect: handleSelectRoom,
    }), [activeRoomId, handleSelectRoom, isResolving, isRoomsLoading, roomItems]);

    useChangeSubSideBar(subSideBarConfig);

    const fetchHistory = useCallback(async () => {
        if (activeRoomId === null) {
            setMessages([]);
            setHasMore(false);
            setOldestMessageId(null);
            return;
        }

        setIsHistoryLoading(true);
        try {
            const response = await fetchApi<MessageHistoryResponse>(
                API_SANDBOX.CHAT_APP_HISTORY,
                { limit: 20 },
                { roomId: activeRoomId }
            );
            const history = response.messages.map(normalizeMessage);
            setMessages(history);
            setHasMore(response.hasMore);
            setOldestMessageId(history.length > 0 ? (history[0].id ?? null) : null);
        } catch (error: unknown) {
            console.error('Failed to fetch chat history:', error);
            notification.error({
                message: 'Error',
                description: getErrorMessage(error, 'Failed to fetch chat history'),
            });
        } finally {
            setIsHistoryLoading(false);
        }
    }, [activeRoomId, notification]);

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

            setMessages((previous) => [...olderMessages, ...previous]);
            setHasMore(response.hasMore);
            setOldestMessageId(olderMessages.length > 0 ? (olderMessages[0].id ?? null) : null);

            requestAnimationFrame(() => {
                if (container) {
                    container.scrollTop = container.scrollHeight - previousScrollHeight;
                }
            });
        } catch (error: unknown) {
            console.error('Failed to load more messages:', error);
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

    useEffect(() => {
        void fetchHistory();
    }, [fetchHistory]);

    const roomSubtitle = selectedRoom
        ? (selectedRoom.isGroup
            ? 'Group chat'
            : selectedRoom.aiModel
                ? `AI model: ${selectedRoom.aiModel}`
                : undefined)
        : undefined;
    const mainBorderClass = 'border-slate-300 dark:border-border-main';
    const secondaryBorderClass = 'border-slate-300 dark:border-border-secondary';
    const inputBorderClass = '!border-slate-300 dark:!border-border-secondary hover:!border-slate-400 dark:hover:!border-border-main';

    const handleSendMessage = async () => {
        if (!inputText.trim() || isLoading || activeRoomId === null || currentUserId === null) return;

        const outgoingMessage: Message = {
            content: inputText,
            role: 'USER',
            senderId: currentUserId,
            roomId: activeRoomId,
        };

        setMessages((previous) => [...previous, outgoingMessage]);
        setInputText('');
        setIsLoading(true);

        try {
            const response = await fetchApi<ChatResponse>(API_SANDBOX.CHAT_APP_MESSAGE, {
                roomId: activeRoomId,
                senderId: currentUserId,
                message: outgoingMessage.content,
            });

            const aiMessage: Message = {
                content: response.reply,
                role: 'AI',
            };
            setMessages((previous) => [...previous, aiMessage]);
        } catch (error: unknown) {
            console.error('Failed to send message:', error);
            notification.error({
                message: 'Error',
                description: getErrorMessage(error, 'Failed to send message'),
            });
        } finally {
            setIsLoading(false);
        }
    };

    if (isResolving || status === 'loading') {
        return (
            <div className="flex h-full items-center justify-center">
                <Spin size="large" />
            </div>
        );
    }

    if (status === 'unauthenticated' || currentUserId === null) {
        return (
            <div className="flex h-full items-center justify-center text-slate-500">
                <p>Please log in to use the chat.</p>
            </div>
        );
    }

    return (
        <div className={`flex h-full min-h-0 flex-1 overflow-hidden rounded-[28px] border bg-background shadow-sm ${mainBorderClass}`}>
            <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
                {isRoomsLoading ? (
                    <div className="flex flex-1 items-center justify-center px-6">
                        <Spin size="large" />
                    </div>
                ) : activeRoomId === null || selectedRoom === null ? (
                    <div className="flex flex-1 items-center justify-center px-6">
                        <Empty
                            description="No rooms available"
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                        />
                    </div>
                ) : (
                    <>
                        <div className={`sticky top-0 z-20 flex items-center justify-between border-b bg-surface px-5 py-4 backdrop-blur supports-[backdrop-filter]:bg-surface/80 ${mainBorderClass}`}>
                            <Space size="middle">
                                <Badge dot color="green" offset={[-5, 35]}>
                                    <Avatar
                                        src="/ai_avatar.png"
                                        size={42}
                                        className={`border bg-muted ${secondaryBorderClass}`}
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

                                    <div className={`flex min-h-full flex-col gap-4 ${messages.length === 0 && !isLoading ? 'items-center justify-center' : 'justify-end'}`}>
                                        {messages.length === 0 && !isLoading && (
                                            <div className="flex max-w-md flex-col items-center text-center text-text-secondary">
                                                <Image
                                                    src="/ai_avatar.png"
                                                    alt="AI Avatar"
                                                    width={80}
                                                    height={80}
                                                    className="mb-4 opacity-45 dark:opacity-60"
                                                />
                                                <p className="m-0">Say hello to start the conversation!</p>
                                            </div>
                                        )}

                                        {messages.map((message, index) => {
                                            const aiMessage = isAiMessage(message);

                                            return (
                                                <div
                                                    key={index}
                                                    className={`flex flex-col ${aiMessage ? 'items-start' : 'items-end'}`}
                                                >
                                                    <div className="flex max-w-[85%] items-end gap-2 md:max-w-[72%]">
                                                        {aiMessage && (
                                                            <Avatar
                                                                src="/ai_avatar.png"
                                                                size={32}
                                                                className={`shrink-0 border bg-muted ${secondaryBorderClass}`}
                                                            />
                                                        )}

                                                        <div
                                                            className={`rounded-3xl p-3 shadow-sm ${aiMessage
                                                                ? `rounded-bl-md border bg-surface text-foreground ${mainBorderClass}`
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

                                        {isLoading && (
                                            <div className="flex justify-start">
                                                <div className="flex max-w-[70%] items-end gap-2">
                                                    <Avatar
                                                        src="/ai_avatar.png"
                                                        size={32}
                                                        className={`border bg-muted ${secondaryBorderClass}`}
                                                    />
                                                    <div className={`flex items-center gap-1 rounded-3xl rounded-bl-md border bg-surface p-4 shadow-sm ${mainBorderClass}`}>
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

                        <div className={`sticky bottom-0 z-20 border-t bg-surface px-4 py-4 backdrop-blur supports-[backdrop-filter]:bg-surface/85 md:px-6 ${mainBorderClass}`}>
                            <div className="mx-auto flex w-full max-w-4xl items-center gap-2">
                                <Input
                                    size="large"
                                    value={inputText}
                                    onChange={(event) => setInputText(event.target.value)}
                                    onPressEnter={handleSendMessage}
                                    placeholder="Type a message..."
                                    disabled={isLoading || isHistoryLoading}
                                    className={`rounded-full !bg-muted px-5 !text-foreground placeholder:!text-text-secondary focus:!border-accent ${inputBorderClass}`}
                                />
                                <Button
                                    type="primary"
                                    shape="circle"
                                    size="large"
                                    icon={<SendOutlined />}
                                    onClick={handleSendMessage}
                                    disabled={!inputText.trim() || isLoading || isHistoryLoading}
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
