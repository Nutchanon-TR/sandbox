'use client';

import React from 'react';
import { Avatar, Spin } from 'antd';
import type { ChatMessage } from '@/interface/ChatApp';
import { CHAT_BORDER } from './styles';
import { MessageBubble } from './MessageBubble';

interface MessagesAreaProps {
    messages: ChatMessage[];
    avatarSrc: string;
    isHistoryLoading: boolean;
    isLoadingMore: boolean;
    isSending: boolean;
    scrollContainerRef: React.RefObject<HTMLDivElement | null>;
    messagesEndRef: React.RefObject<HTMLDivElement | null>;
    onScroll: (event: React.UIEvent<HTMLDivElement>) => void;
    isAiMessage: (message: ChatMessage) => boolean;
}

function EmptyConversation({ avatarSrc }: { avatarSrc: string }) {
    return (
        <div className="flex max-w-md flex-col items-center text-center text-text-secondary">
            <Avatar
                src={avatarSrc}
                size={80}
                className={`mb-4 bg-muted opacity-45 dark:opacity-60 ${CHAT_BORDER.secondary}`}
            />
            <p className="m-0">Say hello to start the conversation!</p>
        </div>
    );
}

function TypingIndicator({ avatarSrc }: { avatarSrc: string }) {
    return (
        <div className="flex justify-start">
            <div className="flex max-w-[70%] items-end gap-2">
                <Avatar
                    src={avatarSrc}
                    size={32}
                    className={`border bg-muted ${CHAT_BORDER.secondary}`}
                />
                <div className={`flex items-center gap-1 rounded-3xl rounded-bl-md border bg-surface p-4 shadow-sm ${CHAT_BORDER.main}`}>
                    <div className="h-2 w-2 animate-bounce rounded-full bg-text-secondary/60 [animation-delay:-0.3s]" />
                    <div className="h-2 w-2 animate-bounce rounded-full bg-text-secondary/60 [animation-delay:-0.15s]" />
                    <div className="h-2 w-2 animate-bounce rounded-full bg-text-secondary/60" />
                </div>
            </div>
        </div>
    );
}

export function MessagesArea({
    messages,
    avatarSrc,
    isHistoryLoading,
    isLoadingMore,
    isSending,
    scrollContainerRef,
    messagesEndRef,
    onScroll,
    isAiMessage,
}: MessagesAreaProps) {
    const showEmptyState = messages.length === 0 && !isSending;

    return (
        <div
            ref={scrollContainerRef}
            onScroll={onScroll}
            className="min-h-0 flex-1 overflow-y-auto overscroll-contain bg-background px-4 py-5 md:px-6"
        >
            {isHistoryLoading ? (
                <div className="flex h-full min-h-0 items-center justify-center">
                    <Spin size="large" />
                </div>
            ) : (
                <>
                    {isLoadingMore && (
                        <div className="flex justify-center py-3">
                            <Spin size="small" />
                        </div>
                    )}

                    <div className={`flex min-h-full flex-col gap-4 ${showEmptyState ? 'items-center justify-center' : 'justify-end'}`}>
                        {showEmptyState && <EmptyConversation avatarSrc={avatarSrc} />}

                        {messages.map((message, index) => (
                            <MessageBubble
                                key={message.id ?? `msg-${index}`}
                                message={message}
                                isAi={isAiMessage(message)}
                                avatarSrc={avatarSrc}
                            />
                        ))}

                        {isSending && <TypingIndicator avatarSrc={avatarSrc} />}
                        <div ref={messagesEndRef} />
                    </div>
                </>
            )}
        </div>
    );
}
