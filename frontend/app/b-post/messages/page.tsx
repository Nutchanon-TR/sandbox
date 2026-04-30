'use client';

import { Avatar, Button, Empty, Input, Spin, Typography, Upload } from 'antd';
import { PictureOutlined, SendOutlined } from '@ant-design/icons';
import Image from 'next/image';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { TITLE } from '@/constants/Title';
import { useChangeTitle } from '@/utils/breadCrumbUtil';
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

export default function MessagesPage() {
    useChangeTitle(TITLE.B_POST, 'MESSAGES');

    const me = useSessionStore((s) => s.internalUserId);
    const realtime = useBPostRealtime();
    const searchParams = useSearchParams();

    const conversations = useBPostStore((s) => s.conversations);
    const setConversations = useBPostStore((s) => s.setConversations);
    const activeId = useBPostStore((s) => s.activeConversationId);
    const setActiveId = useBPostStore((s) => s.setActiveConversationId);
    const messages = useBPostStore((s) => s.messages);
    const setMessages = useBPostStore((s) => s.setMessages);
    const prependMessages = useBPostStore((s) => s.prependMessages);
    const clearUnread = useBPostStore((s) => s.clearUnread);

    const [loadingList, setLoadingList] = useState(false);
    const [loadingHistory, setLoadingHistory] = useState(false);
    const [hasMore, setHasMore] = useState(false);
    const [cursor, setCursor] = useState<number | null>(null);
    const [text, setText] = useState('');
    const [uploading, setUploading] = useState(false);
    const endRef = useRef<HTMLDivElement>(null);

    const queryConvId = Number(searchParams.get('conversationId') ?? '') || null;

    const activeConv = conversations.find((c) => c.id === activeId) ?? null;

    const refreshList = useCallback(async () => {
        setLoadingList(true);
        try {
            setConversations(await fetchApi<ConversationDto[]>(API_SANDBOX.B_POST_CONVERSATION_LIST));
        } finally {
            setLoadingList(false);
        }
    }, [setConversations]);

    useEffect(() => {
        if (me != null) refreshList();
    }, [me, refreshList]);

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
                // backend returns newest-first; reverse for chronological display
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
        endRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages]);

    const loadMore = async () => {
        if (!hasMore || cursor == null || activeId == null || loadingHistory) return;
        setLoadingHistory(true);
        try {
            const res = await fetchApi<PageResponse<MessageDto>>(
                API_SANDBOX.B_POST_CONVERSATION_HISTORY,
                { beforeId: cursor, limit: 30 },
                { conversationId: activeId }
            );
            prependMessages([...res.items].reverse());
            setHasMore(res.hasMore);
            setCursor(res.nextCursor);
        } finally {
            setLoadingHistory(false);
        }
    };

    const handleSend = async () => {
        const content = text.trim();
        if (!content || activeId == null) return;
        setText('');
        try {
            await fetchApi<MessageDto>(API_SANDBOX.B_POST_MESSAGE_SEND, {
                conversationId: activeId,
                content,
            });
            // STOMP push will deliver the message back via /user/queue/messages
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

    return (
        <div className="mx-auto flex h-full w-full max-w-6xl gap-3 pb-4">
            {/* Conversation list */}
            <aside className="hidden w-72 shrink-0 flex-col rounded-3xl border bg-surface p-3 md:flex">
                <h3 className="mb-2 px-2">Conversations</h3>
                {loadingList ? (
                    <div className="flex justify-center py-6"><Spin /></div>
                ) : conversations.length === 0 ? (
                    <Empty description="No conversations" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                ) : (
                    <ul className="flex flex-col gap-1 overflow-auto">
                        {conversations.map((c) => (
                            <li key={c.id}>
                                <button
                                    type="button"
                                    onClick={() => openConversation(c.id)}
                                    className={`flex w-full items-center gap-2 rounded-2xl px-2 py-2 text-left hover:bg-muted ${
                                        c.id === activeId ? 'bg-muted' : ''
                                    }`}
                                >
                                    <div className="relative">
                                        <Avatar src={c.otherUser?.avatarUrl ?? undefined} size={40}>
                                            {c.otherUser?.displayName?.[0]}
                                        </Avatar>
                                        <div className="absolute -bottom-0.5 -right-0.5">
                                            <PresenceDot userId={c.otherUser?.id} />
                                        </div>
                                    </div>
                                    <div className="min-w-0 flex-1">
                                        <div className="flex items-baseline justify-between gap-2">
                                            <Text strong className="truncate">{c.otherUser?.displayName}</Text>
                                            <span className="shrink-0 text-xs text-text-secondary">{formatRelative(c.lastMessageAt)}</span>
                                        </div>
                                        <div className="flex items-center justify-between gap-2">
                                            <span className="truncate text-xs text-text-secondary">
                                                {c.lastMessage?.content || (c.lastMessage?.imageUrl ? '📷 Photo' : 'Say hi…')}
                                            </span>
                                            {c.unreadCount > 0 && (
                                                <span className="rounded-full bg-blue-500 px-2 text-xs text-white">{c.unreadCount}</span>
                                            )}
                                        </div>
                                    </div>
                                </button>
                            </li>
                        ))}
                    </ul>
                )}
            </aside>

            {/* Active conversation */}
            <main className="flex flex-1 flex-col rounded-3xl border bg-surface">
                {activeConv == null ? (
                    <div className="flex flex-1 items-center justify-center text-text-secondary">
                        Select a conversation to start chatting.
                    </div>
                ) : (
                    <>
                        <header className="flex items-center justify-between border-b px-5 py-3">
                            <div className="flex items-center gap-2">
                                <Avatar src={activeConv.otherUser?.avatarUrl ?? undefined}>
                                    {activeConv.otherUser?.displayName?.[0]}
                                </Avatar>
                                <div>
                                    <Text strong>{activeConv.otherUser?.displayName}</Text>
                                    <div className="flex items-center gap-1 text-xs text-text-secondary">
                                        <PresenceDot userId={activeConv.otherUser?.id} />
                                        <span>online status</span>
                                    </div>
                                </div>
                            </div>
                        </header>

                        <div className="flex-1 space-y-3 overflow-auto px-4 py-4">
                            {hasMore && (
                                <div className="flex justify-center">
                                    <Button size="small" onClick={loadMore} loading={loadingHistory}>Load older</Button>
                                </div>
                            )}
                            {messages.map((m) => {
                                const mine = m.senderId === me;
                                return (
                                    <div key={m.id} className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
                                        <div className={`max-w-[70%] rounded-2xl px-3 py-2 shadow-sm ${mine ? 'bg-blue-500 text-white' : 'bg-muted'}`}>
                                            {m.content && <p className="m-0 whitespace-pre-wrap text-sm">{m.content}</p>}
                                            {m.imageUrl && (
                                                <Image src={m.imageUrl} alt="msg" width={240} height={240} className="rounded-xl object-cover" />
                                            )}
                                            <div className={`mt-1 text-[10px] ${mine ? 'text-blue-100' : 'text-text-secondary'}`}>
                                                {formatRelative(m.createdAt)}
                                                {mine && m.readAt && <span className="ml-1">· read</span>}
                                            </div>
                                        </div>
                                    </div>
                                );
                            })}
                            <div ref={endRef} />
                        </div>

                        <footer className="flex items-center gap-2 border-t px-3 py-3">
                            <Upload beforeUpload={(f) => handleUpload(f as File)} showUploadList={false} accept="image/*">
                                <Button icon={<PictureOutlined />} loading={uploading} type="text" />
                            </Upload>
                            <Input
                                value={text}
                                onChange={(e) => setText(e.target.value)}
                                onPressEnter={handleSend}
                                placeholder="Type a message…"
                                size="large"
                                className="flex-1"
                            />
                            <Button type="primary" shape="circle" icon={<SendOutlined />} size="large" onClick={handleSend} disabled={!text.trim()} />
                        </footer>
                    </>
                )}
            </main>
        </div>
    );
}
