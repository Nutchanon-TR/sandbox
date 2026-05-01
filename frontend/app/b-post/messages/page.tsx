'use client';

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Avatar, Button, Empty, Input, Space, Spin, Typography, Upload } from 'antd';
import Image from 'next/image';
import { MoreOutlined, PhoneOutlined, PictureOutlined, SendOutlined, VideoCameraOutlined } from '@ant-design/icons';
import { usePathname, useSearchParams } from 'next/navigation';
import { TITLE } from '@/constants/Title';
import { useChangeTitle } from '@/utils/breadCrumbUtil';
import { useChangeSubSideBar } from '@/utils/subSideBarUtil';
import { useLoadingContext } from '@/providers/LoadingBarProvider';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { fetchApi } from '@/utils/api';
import api from '@/config/axiosConfig';
import { useSessionStore } from '@/stores/sessionStore';
import { useBPostStore } from '@/stores/bpostStore';
import { ConversationDto, MessageDto, PageResponse } from '@/interface/BPost';
import { useBPostRealtime } from '@/providers/BPostRealtimeProvider';
import { formatRelative } from '@/utils/time';
import { PresenceDot } from '@/components/Bpost/PresenceDot';

const { Text } = Typography;

const BORDER = {
    main: 'border-slate-300 dark:border-border-main',
    secondary: 'border-slate-300 dark:border-border-secondary',
    input: '!border-slate-300 dark:!border-border-secondary hover:!border-slate-400 dark:hover:!border-border-main',
} as const;

export default function MessagesPage() {
    useChangeTitle(TITLE.B_POST, 'MESSAGES');

    const me = useSessionStore((s) => s.internalUserId);
    const realtime = useBPostRealtime();
    const pathname = usePathname();
    const searchParams = useSearchParams();

    const conversations = useBPostStore((s) => s.conversations);
    const setConversations = useBPostStore((s) => s.setConversations);
    const activeId = useBPostStore((s) => s.activeConversationId);
    const setActiveId = useBPostStore((s) => s.setActiveConversationId);
    const messages = useBPostStore((s) => s.messages);
    const setMessages = useBPostStore((s) => s.setMessages);
    const prependMessages = useBPostStore((s) => s.prependMessages);
    const clearUnread = useBPostStore((s) => s.clearUnread);

    const { setIsLoading } = useLoadingContext();
    const [loadingList, setLoadingList] = useState(false);
    const [initialLoaded, setInitialLoaded] = useState(false);
    const [loadingHistory, setLoadingHistory] = useState(false);
    const [isLoadingMore, setIsLoadingMore] = useState(false);
    const [hasMore, setHasMore] = useState(false);
    const [cursor, setCursor] = useState<number | null>(null);
    const [text, setText] = useState('');
    const [uploading, setUploading] = useState(false);

    const messagesEndRef = useRef<HTMLDivElement>(null);
    const scrollContainerRef = useRef<HTMLDivElement>(null);

    const queryConvId = Number(searchParams.get('conversationId') ?? '') || null;
    const activeConv = conversations.find((c) => c.id === activeId) ?? null;

    const refreshList = useCallback(async () => {
        setLoadingList(true);
        try {
            setConversations(await fetchApi<ConversationDto[]>(API_SANDBOX.B_POST_CONVERSATION_LIST));
        } finally {
            setLoadingList(false);
            setInitialLoaded(true);
        }
    }, [setConversations]);

    useEffect(() => {
        if (me != null) refreshList();
    }, [me, refreshList]);

    // Drive the global loading overlay until the initial conversation list has loaded
    useEffect(() => {
        const blocking = me === null || (!initialLoaded && loadingList);
        setIsLoading(blocking);
        return () => setIsLoading(false);
    }, [me, initialLoaded, loadingList, setIsLoading]);

    useEffect(() => {
        if (queryConvId && queryConvId !== activeId) setActiveId(queryConvId);
    }, [queryConvId, activeId, setActiveId]);

    const openConversation = useCallback(
        async (id: number) => {
            setActiveId(id);
            setLoadingHistory(true);
            try {
                const res = await fetchApi<PageResponse<MessageDto>>(
                    API_SANDBOX.B_POST_CONVERSATION_HISTORY,
                    { limit: 30 },
                    { conversationId: id }
                );
                setMessages([...res.items].reverse());
                setHasMore(res.hasMore);
                setCursor(res.nextCursor);
                clearUnread(id);
                await fetchApi(API_SANDBOX.B_POST_CONVERSATION_READ, {}, { conversationId: id });
                realtime.publish('/app/message.read', { conversationId: id });
            } finally {
                setLoadingHistory(false);
            }
        },
        [setActiveId, setMessages, clearUnread, realtime]
    );

    useEffect(() => {
        if (activeId != null) openConversation(activeId);
    }, [activeId]); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages, loadingHistory]);

    // ── SubSideBar config (conversation list) ──
    const conversationItems = useMemo(
        () =>
            conversations.map((c) => ({
                key: c.id,
                label: c.otherUser?.displayName ?? 'Unknown',
                description:
                    c.lastMessage?.content ||
                    (c.lastMessage?.imageUrl ? '📷 Photo' : 'Say hi…'),
                icon: (
                    <div className="relative">
                        <Avatar src={c.otherUser?.avatarUrl ?? undefined} size={36}>
                            {c.otherUser?.displayName?.[0]}
                        </Avatar>
                        <div className="absolute -bottom-0.5 -right-0.5">
                            <PresenceDot userId={c.otherUser?.id} />
                        </div>
                    </div>
                ),
                badge: c.unreadCount,
            })),
        [conversations]
    );

    const handleSelectConversation = useCallback(
        (key: string | number) => {
            const id = Number(key);
            const nextParams = new URLSearchParams(searchParams.toString());
            nextParams.set('conversationId', String(id));
            window.history.replaceState(null, '', `${pathname}?${nextParams.toString()}`);
            void openConversation(id);
        },
        [openConversation, pathname, searchParams]
    );

    const subSideBarConfig = useMemo(
        () => ({
            title: 'Conversations',
            items: conversationItems,
            selectedKey: activeId ?? undefined,
            emptyText: 'No conversations',
            loading: me === null || loadingList,
            onSelect: handleSelectConversation,
        }),
        [conversationItems, activeId, me, loadingList, handleSelectConversation]
    );

    useChangeSubSideBar(subSideBarConfig);

    const loadMoreMessages = useCallback(async () => {
        if (!hasMore || cursor == null || activeId == null || isLoadingMore) return;

        setIsLoadingMore(true);
        const container = scrollContainerRef.current;
        const previousScrollHeight = container?.scrollHeight ?? 0;

        try {
            const res = await fetchApi<PageResponse<MessageDto>>(
                API_SANDBOX.B_POST_CONVERSATION_HISTORY,
                { beforeId: cursor, limit: 30 },
                { conversationId: activeId }
            );
            prependMessages([...res.items].reverse());
            setHasMore(res.hasMore);
            setCursor(res.nextCursor);

            requestAnimationFrame(() => {
                if (container) {
                    container.scrollTop = container.scrollHeight - previousScrollHeight;
                }
            });
        } finally {
            setIsLoadingMore(false);
        }
    }, [hasMore, cursor, activeId, isLoadingMore, prependMessages]);

    const handleScroll = useCallback(
        (event: React.UIEvent<HTMLDivElement>) => {
            if (event.currentTarget.scrollTop === 0 && hasMore && !isLoadingMore) {
                void loadMoreMessages();
            }
        },
        [hasMore, isLoadingMore, loadMoreMessages]
    );

    const handleSend = async () => {
        const content = text.trim();
        if (!content || activeId == null) return;
        setText('');
        try {
            await fetchApi<MessageDto>(API_SANDBOX.B_POST_MESSAGE_SEND, {
                conversationId: activeId,
                content,
            });
        } catch (e) {
            console.error(e);
            setText(content);
        }
    };

    const handleUpload = async (file: File) => {
        if (activeId == null) return false;
        setUploading(true);
        try {
            const form = new FormData();
            form.append('imageFile', file);
            const { data } = await api.post<{ url: string }>(API_SANDBOX.B_POST_MESSAGE_UPLOAD_IMAGE.path, form);
            await fetchApi(API_SANDBOX.B_POST_MESSAGE_SEND, {
                conversationId: activeId,
                imageUrl: data.url,
            });
        } catch (e) {
            console.error(e);
        } finally {
            setUploading(false);
        }
        return false;
    };

    const isSyncing = me === null || !initialLoaded;

    return (
        <div className={`flex h-full min-h-0 flex-1 overflow-hidden rounded-[28px] border bg-background shadow-sm ${BORDER.main}`}>
            <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
                {isSyncing ? (
                    <div className="flex flex-1 items-center justify-center px-6">
                        <Spin size="large" />
                    </div>
                ) : activeConv == null ? (
                    <div className="flex flex-1 items-center justify-center px-6">
                        <Empty description="Select a conversation to start chatting." image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    </div>
                ) : (
                    <>
                        {/* Header */}
                        <div className={`sticky top-0 z-20 flex items-center justify-between border-b bg-surface px-5 py-4 backdrop-blur supports-[backdrop-filter]:bg-surface/80 ${BORDER.main}`}>
                            <Space size="middle">
                                <div className="relative">
                                    <Avatar
                                        src={activeConv.otherUser?.avatarUrl ?? undefined}
                                        size={42}
                                        className={`border bg-muted ${BORDER.secondary}`}
                                    >
                                        {activeConv.otherUser?.displayName?.[0]}
                                    </Avatar>
                                    <div className="absolute -bottom-0.5 -right-0.5">
                                        <PresenceDot userId={activeConv.otherUser?.id} />
                                    </div>
                                </div>
                                <div className="flex flex-col">
                                    <Text strong className="text-lg leading-none !text-foreground">
                                        {activeConv.otherUser?.displayName}
                                    </Text>
                                    <Text type="secondary" className="mt-1 text-xs font-medium">
                                        online status
                                    </Text>
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
                            {loadingHistory ? (
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

                                    <div className={`flex min-h-full flex-col gap-4 ${messages.length === 0 ? 'items-center justify-center' : 'justify-end'}`}>
                                        {messages.length === 0 && (
                                            <div className="flex max-w-md flex-col items-center text-center text-text-secondary">
                                                <p className="m-0">Say hello to start the conversation!</p>
                                            </div>
                                        )}

                                        {messages.map((m) => {
                                            const mine = m.senderId === me;
                                            return (
                                                <div
                                                    key={m.id}
                                                    className={`flex flex-col ${mine ? 'items-end' : 'items-start'}`}
                                                >
                                                    <div className="flex max-w-[85%] items-end gap-2 md:max-w-[72%]">
                                                        {!mine && (
                                                            <Avatar
                                                                src={activeConv.otherUser?.avatarUrl ?? undefined}
                                                                size={32}
                                                                className={`shrink-0 border bg-muted ${BORDER.secondary}`}
                                                            >
                                                                {activeConv.otherUser?.displayName?.[0]}
                                                            </Avatar>
                                                        )}
                                                        <div
                                                            className={`rounded-3xl p-3 shadow-sm ${mine
                                                                ? 'rounded-br-md bg-blue-600 text-white dark:bg-blue-500'
                                                                : `rounded-bl-md border bg-surface text-foreground ${BORDER.main}`
                                                                }`}
                                                        >
                                                            {m.content && (
                                                                <p className="m-0 whitespace-pre-wrap break-words text-sm leading-relaxed">
                                                                    {m.content}
                                                                </p>
                                                            )}
                                                            {m.imageUrl && (
                                                                <Image
                                                                    src={m.imageUrl}
                                                                    alt="msg"
                                                                    width={240}
                                                                    height={240}
                                                                    className="rounded-xl object-cover"
                                                                />
                                                            )}
                                                            <div className={`mt-1 text-[10px] ${mine ? 'text-blue-100' : 'text-text-secondary'}`}>
                                                                {formatRelative(m.createdAt)}
                                                                {mine && m.readAt && <span className="ml-1">· read</span>}
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>
                                            );
                                        })}

                                        <div ref={messagesEndRef} />
                                    </div>
                                </>
                            )}
                        </div>

                        {/* Input bar */}
                        <div className={`sticky bottom-0 z-20 border-t bg-surface px-4 py-4 backdrop-blur supports-[backdrop-filter]:bg-surface/85 md:px-6 ${BORDER.main}`}>
                            <div className="mx-auto flex w-full max-w-4xl items-center gap-2">
                                <Upload beforeUpload={(f) => handleUpload(f as File)} showUploadList={false} accept="image/*">
                                    <Button icon={<PictureOutlined />} loading={uploading} type="text" size="large" />
                                </Upload>
                                <Input
                                    size="large"
                                    value={text}
                                    onChange={(e) => setText(e.target.value)}
                                    onPressEnter={handleSend}
                                    placeholder="Type a message..."
                                    disabled={loadingHistory}
                                    className={`rounded-full !bg-muted px-5 !text-foreground placeholder:!text-text-secondary focus:!border-accent ${BORDER.input}`}
                                />
                                <Button
                                    type="primary"
                                    shape="circle"
                                    size="large"
                                    icon={<SendOutlined />}
                                    onClick={handleSend}
                                    disabled={!text.trim() || loadingHistory}
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
