'use client';

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Avatar, Badge, Button, Input, Space, Spin, Typography } from 'antd';
import Image from 'next/image';
import { TITLE } from "@/constants/Title";
import { useChangeTitle } from "@/utils/breadCrumbUtil";
import { fetchApi } from '@/utils/api';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { useNotification } from '@/context/NotificationContext';
import { useSupabaseSession } from '@/hooks/useSupabaseSession';
import {
    MoreOutlined,
    PhoneOutlined,
    SendOutlined,
    VideoCameraOutlined,
} from "@ant-design/icons";

const { Text } = Typography;

interface Message {
    id?: number;
    roomId?: number;
    senderId?: number;
    senderUsername?: string;
    senderRole?: string;
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

export default function MessagePage() {
    const [messages, setMessages] = useState<Message[]>([]);
    const [inputText, setInputText] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [isResolving, setIsResolving] = useState(true);
    const [roomId, setRoomId] = useState<number | null>(null);
    const [currentUserId, setCurrentUserId] = useState<number | null>(null);
    const [hasMore, setHasMore] = useState(false);
    const [isLoadingMore, setIsLoadingMore] = useState(false);
    const [oldestMessageId, setOldestMessageId] = useState<number | null>(null);
    const notification = useNotification();
    const messagesEndRef = useRef<HTMLDivElement>(null);
    const scrollContainerRef = useRef<HTMLDivElement>(null);
    const { data: session, status } = useSupabaseSession();

    useChangeTitle(TITLE.CHAT_APP, "MESSAGE");

    // Resolve Supabase session → chat user ID + room ID
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
                setRoomId(response.roomId);
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

    useEffect(() => {
        scrollToBottom();
    }, [messages, isLoading]);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    };

    const fetchHistory = useCallback(async () => {
        if (roomId === null) return;
        try {
            const response = await fetchApi<MessageHistoryResponse>(
                API_SANDBOX.CHAT_APP_HISTORY,
                { limit: 20 },
                { roomId }
            );
            const history: Message[] = response.messages.map((msg): Message => ({
                ...msg,
                role: msg.senderRole === 'AI' ? 'AI' : 'USER',
            }));
            setMessages(history);
            setHasMore(response.hasMore);
            setOldestMessageId(history.length > 0 ? (history[0].id ?? null) : null);
        } catch (error: unknown) {
            console.error('Failed to fetch chat history:', error);
            notification.error({
                message: 'Error',
                description: getErrorMessage(error, 'Failed to fetch chat history'),
            });
        }
    }, [notification, roomId]);

    const loadMoreMessages = useCallback(async () => {
        if (roomId === null || !hasMore || isLoadingMore || oldestMessageId === null) return;
        setIsLoadingMore(true);
        const container = scrollContainerRef.current;
        const prevScrollHeight = container?.scrollHeight ?? 0;
        try {
            const response = await fetchApi<MessageHistoryResponse>(
                API_SANDBOX.CHAT_APP_HISTORY,
                { beforeId: oldestMessageId, limit: 20 },
                { roomId }
            );
            const older: Message[] = response.messages.map((msg): Message => ({
                ...msg,
                role: msg.senderRole === 'AI' ? 'AI' : 'USER',
            }));
            setMessages((prev) => [...older, ...prev]);
            setHasMore(response.hasMore);
            setOldestMessageId(older.length > 0 ? (older[0].id ?? null) : null);
            // Restore scroll position so the view doesn't jump
            requestAnimationFrame(() => {
                if (container) {
                    container.scrollTop = container.scrollHeight - prevScrollHeight;
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
    }, [roomId, hasMore, isLoadingMore, oldestMessageId, notification]);

    const handleScroll = useCallback((e: React.UIEvent<HTMLDivElement>) => {
        if (e.currentTarget.scrollTop === 0 && hasMore && !isLoadingMore) {
            void loadMoreMessages();
        }
    }, [hasMore, isLoadingMore, loadMoreMessages]);

    useEffect(() => {
        void fetchHistory();
    }, [fetchHistory]);

    const handleSendMessage = async () => {
        if (!inputText.trim() || isLoading || roomId === null || currentUserId === null) return;

        const newMsg: Message = {
            content: inputText,
            role: 'USER',
            senderId: currentUserId,
            roomId: roomId,
        };

        setMessages((prev) => [...prev, newMsg]);
        setInputText('');
        setIsLoading(true);

        try {
            const response = await fetchApi<ChatResponse>(API_SANDBOX.CHAT_APP_MESSAGE, {
                roomId: roomId,
                senderId: currentUserId,
                message: newMsg.content,
            });

            const aiMsg: Message = {
                content: response.reply,
                role: 'AI',
            };
            setMessages((prev) => [...prev, aiMsg]);
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

    if (status === 'unauthenticated' || roomId === null || currentUserId === null) {
        return (
            <div className="flex h-full items-center justify-center text-slate-500">
                <p>Please log in to use the chat.</p>
            </div>
        );
    }

    return (
        <div className="flex h-full min-h-0 flex-1 flex-col overflow-hidden rounded-[28px] border border-border-main bg-background shadow-sm">
            <div
                className="sticky top-0 z-20 flex items-center justify-between border-b border-border-main bg-surface px-5 py-4 backdrop-blur supports-[backdrop-filter]:bg-surface/80"
            >
                <Space size="middle">
                    <Badge dot color="green" offset={[-5, 35]}>
                        <Avatar
                            src="/ai_avatar.png"
                            size={42}
                            className="border border-border-secondary bg-muted"
                        />
                    </Badge>
                    <div className="flex flex-col">
                        <Text strong className="text-lg leading-none !text-foreground">
                            AI Assistant
                        </Text>
                        <Text type="success" className="mt-1 text-xs font-medium">
                            Active now
                        </Text>
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
                {isLoadingMore && (
                    <div className="flex justify-center py-3">
                        <Spin size="small" />
                    </div>
                )}

                {messages.length === 0 && !isLoading && (
                    <div
                        className="mx-auto mt-20 flex max-w-md flex-col items-center justify-center text-center text-text-secondary"
                    >
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

                <div className="flex min-h-full flex-col justify-end gap-4">
                    {messages.map((msg, idx) => (
                        <div
                            key={idx}
                            className={`flex flex-col ${msg.role === 'USER' ? 'items-end' : 'items-start'}`}
                        >
                            <div className="flex max-w-[85%] items-end gap-2 md:max-w-[72%]">
                                {msg.role === 'AI' && (
                                    <Avatar
                                        src="/ai_avatar.png"
                                        size={32}
                                        className="shrink-0 border border-border-secondary bg-muted"
                                    />
                                )}

                                <div
                                    className={`rounded-3xl p-3 shadow-sm ${msg.role === 'USER'
                                        ? 'rounded-br-md bg-blue-600 text-white dark:bg-blue-500'
                                        : 'rounded-bl-md border border-border-main bg-surface text-foreground'
                                        }`}
                                >
                                    <p className="m-0 whitespace-pre-wrap break-words text-sm leading-relaxed">
                                        {msg.content}
                                    </p>
                                </div>
                            </div>
                        </div>
                    ))}

                    {isLoading && (
                        <div className="flex justify-start">
                            <div className="flex max-w-[70%] items-end gap-2">
                                <Avatar
                                    src="/ai_avatar.png"
                                    size={32}
                                    className="border border-border-secondary bg-muted"
                                />
                                <div
                                    className="flex items-center gap-1 rounded-3xl rounded-bl-md border border-border-main bg-surface p-4 shadow-sm"
                                >
                                    <div className="h-2 w-2 animate-bounce rounded-full bg-text-secondary/60 [animation-delay:-0.3s]" />
                                    <div className="h-2 w-2 animate-bounce rounded-full bg-text-secondary/60 [animation-delay:-0.15s]" />
                                    <div className="h-2 w-2 animate-bounce rounded-full bg-text-secondary/60" />
                                </div>
                            </div>
                        </div>
                    )}

                    <div ref={messagesEndRef} />
                </div>
            </div>

            <div
                className="sticky bottom-0 z-20 border-t border-border-main bg-surface px-4 py-4 backdrop-blur supports-[backdrop-filter]:bg-surface/85 md:px-6"
            >
                <div className="mx-auto flex w-full max-w-4xl items-center gap-2">
                    <Input
                        size="large"
                        value={inputText}
                        onChange={(e) => setInputText(e.target.value)}
                        onPressEnter={handleSendMessage}
                        placeholder="Type a message..."
                        disabled={isLoading}
                        className="rounded-full !border-border-secondary !bg-muted px-5 !text-foreground placeholder:!text-text-secondary hover:!border-border-main focus:!border-accent"
                    />
                    <Button
                        type="primary"
                        shape="circle"
                        size="large"
                        icon={<SendOutlined />}
                        onClick={handleSendMessage}
                        disabled={!inputText.trim() || isLoading}
                        className="flex items-center justify-center"
                    />
                </div>
            </div>
        </div>
    );
}
