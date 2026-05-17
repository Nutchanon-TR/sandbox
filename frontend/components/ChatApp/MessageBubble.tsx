'use client';

import { Avatar } from 'antd';
import type { ChatMessage } from '@/interface/ChatApp';
import { CHAT_BORDER } from './styles';

interface MessageBubbleProps {
    message: ChatMessage;
    isAi: boolean;
    avatarSrc: string;
}

export function MessageBubble({ message, isAi, avatarSrc }: MessageBubbleProps) {
    return (
        <div className={`flex flex-col ${isAi ? 'items-start' : 'items-end'}`}>
            <div className="flex max-w-[85%] items-end gap-2 md:max-w-[72%]">
                {isAi && (
                    <Avatar
                        src={avatarSrc}
                        size={32}
                        className={`shrink-0 border bg-muted ${CHAT_BORDER.secondary}`}
                    />
                )}
                <div
                    className={`rounded-3xl p-3 shadow-sm ${isAi
                        ? `rounded-bl-md border bg-surface text-foreground ${CHAT_BORDER.main}`
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
}

