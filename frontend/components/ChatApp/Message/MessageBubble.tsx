'use client';

import { Avatar } from 'antd';
import Image from 'next/image';
import type { ChatMessage } from '@/interface/ChatApp';
import { CHAT_BORDER } from './styles';

interface MessageBubbleProps {
    message: ChatMessage;
    isAi: boolean;
    avatarSrc: string;
}

export function MessageBubble({ message, isAi, avatarSrc }: MessageBubbleProps) {
    const imageAttachments = message.attachments?.filter((attachment) => attachment.type === 'image' && attachment.url) ?? [];

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
                    {imageAttachments.length > 0 && (
                        <div className="mt-3 grid gap-2">
                            {imageAttachments.map((attachment, index) => (
                                <a
                                    key={attachment.id ?? `${attachment.url}-${index}`}
                                    href={attachment.url}
                                    target="_blank"
                                    rel="noreferrer"
                                    className="block overflow-hidden rounded-xl border border-black/10 bg-black/5 dark:border-white/10 dark:bg-white/5"
                                >
                                    <Image
                                        src={attachment.url}
                                        alt="Generated image"
                                        width={360}
                                        height={240}
                                        sizes="(max-width: 768px) 72vw, 360px"
                                        className="h-auto max-h-[320px] w-full object-cover"
                                    />
                                </a>
                            ))}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
