'use client';

import { Button, Input } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import { CHAT_BORDER } from './styles';

interface MessageInputBarProps {
    value: string;
    disabled: boolean;
    isSending: boolean;
    onChange: (value: string) => void;
    onSend: () => void;
    onWheel?: (event: React.WheelEvent<HTMLDivElement>) => void;
}

export function MessageInputBar({
    value,
    disabled,
    isSending,
    onChange,
    onSend,
    onWheel,
}: MessageInputBarProps) {
    return (
        <div
            onWheel={onWheel}
            className={`sticky bottom-0 z-20 shrink-0 border-t bg-surface px-4 py-3 md:px-6 ${CHAT_BORDER.main}`}
        >
            <div className="mx-auto flex w-full max-w-4xl items-center gap-2">
                <Input
                    size="large"
                    value={value}
                    onChange={(event) => onChange(event.target.value)}
                    onPressEnter={onSend}
                    placeholder="Type a message..."
                    disabled={disabled}
                    className={`rounded-full !bg-muted px-5 !text-foreground placeholder:!text-text-secondary focus:!border-accent ${CHAT_BORDER.input}`}
                />
                <Button
                    type="primary"
                    shape="circle"
                    size="large"
                    icon={<SendOutlined />}
                    onClick={onSend}
                    disabled={!value.trim() || disabled}
                    loading={isSending}
                    className="flex shrink-0 items-center justify-center"
                />
            </div>
        </div>
    );
}
