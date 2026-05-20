'use client';

import { Avatar, Badge, Button, Typography } from 'antd';
import {
    MoreOutlined,
    PhoneOutlined,
    RobotOutlined,
    VideoCameraOutlined,
} from '@ant-design/icons';
import type { RoomSummary } from '@/interface/ChatApp';
import { CHAT_BORDER } from './styles';

const { Text } = Typography;

interface ChatHeaderProps {
    room: RoomSummary;
    subtitle?: string;
    avatarSrc: string;
    onWheel?: (event: React.WheelEvent<HTMLDivElement>) => void;
}

export function ChatHeader({ room, subtitle, avatarSrc, onWheel }: ChatHeaderProps) {
    return (
        <div
            onWheel={onWheel}
            className={`sticky top-0 z-20 shrink-0 border-b bg-surface px-4 py-3 md:px-5 ${CHAT_BORDER.main}`}
        >
            <div className="flex min-w-0 items-center justify-between gap-3">
                <div className="flex min-w-0 items-center gap-3">
                    <Badge dot color="green" offset={[-5, 35]}>
                        <Avatar
                            src={avatarSrc}
                            icon={<RobotOutlined />}
                            size={42}
                            className={`border bg-muted ${CHAT_BORDER.secondary}`}
                        />
                    </Badge>
                    <div className="flex min-w-0 flex-col">
                        <Text strong className="truncate text-base leading-tight !text-foreground md:text-lg">
                            {room.name}
                        </Text>
                        {subtitle && (
                            <Text type="secondary" className="mt-1 truncate text-xs font-medium">
                                {subtitle}
                            </Text>
                        )}
                    </div>
                </div>

                <div className="flex shrink-0 items-center gap-1">
                    <Button type="text" className="hidden !text-text-secondary hover:!bg-muted sm:inline-flex" icon={<PhoneOutlined />} />
                    <Button type="text" className="hidden !text-text-secondary hover:!bg-muted sm:inline-flex" icon={<VideoCameraOutlined />} />
                    <Button type="text" className="!text-text-secondary hover:!bg-muted" icon={<MoreOutlined />} />
                </div>
            </div>
        </div>
    );
}
