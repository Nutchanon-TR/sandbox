'use client';

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Avatar, Badge, Button, Input, Space, Typography } from 'antd';
import Image from 'next/image';
import { TITLE } from "@/constants/Title";
import { useChangeTitle } from "@/utils/breadCrumbUtil";
import { fetchApi } from '@/utils/api';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { useNotification } from '@/context/NotificationContext';
import { useTheme } from "@/context/ThemeContext";
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
    const notification = useNotification();
    const messagesEndRef = useRef<HTMLDivElement>(null);
    const { theme } = useTheme();

    const ROOM_ID = 1;
    // const CURRENT_USER_ID = session?.user?.id ? Number(session.user.id) : 1;
    const CURRENT_USER_ID = 1;

    useChangeTitle(TITLE.CHAT_APP, "MESSAGE");

    useEffect(() => {
        scrollToBottom();
    }, [messages, isLoading]);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    };

    const fetchHistory = useCallback(async () => {
        try {
            const response = await fetchApi<Message[]>(
                API_SANDBOX.CHAT_APP_HISTORY,
                {},
                { roomId: ROOM_ID }
            );
            const history: Message[] = response.map((msg): Message => ({
                ...msg,
                role: msg.senderRole === 'AI' ? 'AI' : 'USER',
            }));
            setMessages(history);
        } catch (error: unknown) {
            console.error('Failed to fetch chat history:', error);
            notification.error({
                message: 'Error',
                description: getErrorMessage(error, 'Failed to fetch chat history'),
            });
        }
    }, [notification, ROOM_ID]);

    useEffect(() => {
        void fetchHistory();
    }, [fetchHistory]);

    const handleSendMessage = async () => {
        if (!inputText.trim() || isLoading) return;

        const newMsg: Message = {
            content: inputText,
            role: 'USER',
            senderId: CURRENT_USER_ID,
            roomId: ROOM_ID,
        };

        setMessages((prev) => [...prev, newMsg]);
        setInputText('');
        setIsLoading(true);

        try {
            const response = await fetchApi<ChatResponse>(API_SANDBOX.CHAT_APP_MESSAGE, {
                roomId: ROOM_ID,
                senderId: CURRENT_USER_ID,
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

    const isDark = theme === "dark";

    return (
        <div className={`flex h-full min-h-0 flex-1 flex-col overflow-hidden rounded-[28px] border shadow-sm ${theme === "dark" ? "dark border-slate-800 bg-slate-950" : "border-slate-200 bg-white"}`}>
            <div
                className="sticky top-0 z-20 flex items-center justify-between border-b bg-white px-5 py-4 backdrop-blur supports-[backdrop-filter]:bg-white/80 dark:border-slate-800 dark:bg-slate-900 dark:supports-[backdrop-filter]:bg-slate-900/85"
            >
                <Space size="middle">
                    <Badge dot color="green" offset={[-5, 35]}>
                        <Avatar
                            src="/ai_avatar.png"
                            size={42}
                            className="border border-slate-200 bg-slate-100 dark:border-slate-700 dark:bg-slate-800"
                        />
                    </Badge>
                    <div className="flex flex-col">
                        <Text strong className="text-lg leading-none !text-slate-900 dark:!text-slate-100">
                            AI Assistant
                        </Text>
                        <Text type="success" className="mt-1 text-xs font-medium">
                            Active now
                        </Text>
                    </div>
                </Space>
                <Space size="small">
                    <Button type="text" className="!text-slate-500 hover:!bg-slate-100 dark:!text-slate-400 dark:hover:!bg-slate-800" icon={<PhoneOutlined />} />
                    <Button type="text" className="!text-slate-500 hover:!bg-slate-100 dark:!text-slate-400 dark:hover:!bg-slate-800" icon={<VideoCameraOutlined />} />
                    <Button type="text" className="!text-slate-500 hover:!bg-slate-100 dark:!text-slate-400 dark:hover:!bg-slate-800" icon={<MoreOutlined />} />
                </Space>
            </div>

            <div
                className="flex-1 overflow-y-auto bg-slate-50 px-4 py-5 dark:bg-slate-950 md:px-6"
            >
                {messages.length === 0 && !isLoading && (
                    <div
                        className="mx-auto mt-20 flex max-w-md flex-col items-center justify-center text-center text-slate-500 dark:text-slate-400"
                    >
                        <Image
                            src="/ai_avatar.png"
                            alt="AI Avatar"
                            width={80}
                            height={80}
                            className={isDark ? 'mb-4 opacity-60' : 'mb-4 opacity-45'}
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
                                        className="shrink-0 border border-slate-200 bg-slate-100 dark:border-slate-700 dark:bg-slate-800"
                                    />
                                )}

                                <div
                                    className={`rounded-3xl p-3 shadow-sm ${msg.role === 'USER'
                                        ? 'rounded-br-md bg-blue-600 text-white dark:bg-blue-500'
                                        : 'rounded-bl-md border border-slate-200 bg-white text-slate-900 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-100'
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
                                    className="border border-slate-200 bg-slate-100 dark:border-slate-700 dark:bg-slate-800"
                                />
                                <div
                                    className="flex items-center gap-1 rounded-3xl rounded-bl-md border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900"
                                >
                                    <div className="h-2 w-2 animate-bounce rounded-full bg-slate-400 [animation-delay:-0.3s] dark:bg-slate-500" />
                                    <div className="h-2 w-2 animate-bounce rounded-full bg-slate-400 [animation-delay:-0.15s] dark:bg-slate-500" />
                                    <div className="h-2 w-2 animate-bounce rounded-full bg-slate-400 dark:bg-slate-500" />
                                </div>
                            </div>
                        </div>
                    )}

                    <div ref={messagesEndRef} />
                </div>
            </div>

            <div
                className="sticky bottom-0 z-20 border-t bg-white px-4 py-4 backdrop-blur supports-[backdrop-filter]:bg-white/85 dark:border-slate-800 dark:bg-slate-900 dark:supports-[backdrop-filter]:bg-slate-900/85 md:px-6"
            >
                <div className="mx-auto flex w-full max-w-4xl items-center gap-2">
                    <Input
                        size="large"
                        value={inputText}
                        onChange={(e) => setInputText(e.target.value)}
                        onPressEnter={handleSendMessage}
                        placeholder="Type a message..."
                        disabled={isLoading}
                        className="rounded-full border-slate-200 !bg-slate-100 px-5 text-slate-900 placeholder:!text-slate-400 hover:!border-slate-300 focus:!border-blue-500 dark:!border-slate-700 dark:!bg-slate-950 dark:!text-slate-100 dark:placeholder:!text-slate-500"
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
