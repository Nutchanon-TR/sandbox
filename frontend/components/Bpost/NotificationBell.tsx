'use client';

import { Badge, Button, Dropdown, Empty, List, Typography } from 'antd';
import { BellOutlined } from '@ant-design/icons';
import { useEffect } from 'react';
import { useBPostStore } from '@/stores/bpostStore';
import { fetchApi } from '@/utils/api';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { NotificationDto } from '@/interface/BPost';
import { formatRelative } from '@/utils/time';

const { Text } = Typography;

export function NotificationBell() {
    const notifications = useBPostStore((s) => s.notifications);
    const unread = useBPostStore((s) => s.unreadCount);
    const setNotifications = useBPostStore((s) => s.setNotifications);
    const markRead = useBPostStore((s) => s.markNotificationRead);

    useEffect(() => {
        fetchApi<NotificationDto[]>(API_SANDBOX.B_POST_NOTIFICATION_LIST, { limit: 30 })
            .then(setNotifications)
            .catch(console.error);
    }, [setNotifications]);

    const handleRead = async (n: NotificationDto) => {
        if (n.readAt) return;
        markRead(n.id);
        try {
            await fetchApi(API_SANDBOX.B_POST_NOTIFICATION_READ, {}, { id: n.id });
        } catch (e) {
            console.error(e);
        }
    };

    const dropdownContent = (
        <div className="w-80 max-h-96 overflow-auto rounded-2xl border bg-surface p-2 shadow-lg">
            {notifications.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No notifications" />
            ) : (
                <List
                    dataSource={notifications}
                    renderItem={(n) => (
                        <List.Item
                            className={`!cursor-pointer rounded-xl !px-3 ${n.readAt ? '' : 'bg-blue-50 dark:bg-blue-950/30'}`}
                            onClick={() => handleRead(n)}
                        >
                            <div className="flex w-full justify-between gap-2">
                                <div className="text-sm">
                                    <Text strong>{n.actor?.displayName ?? 'System'}</Text>
                                    <span className="ml-1 text-text-secondary">{n.message}</span>
                                </div>
                                <span className="shrink-0 text-xs text-text-secondary">{formatRelative(n.createdAt)}</span>
                            </div>
                        </List.Item>
                    )}
                />
            )}
        </div>
    );

    return (
        <Dropdown popupRender={() => dropdownContent} trigger={['click']} placement="bottomRight">
            <Badge count={unread} size="small">
                <Button type="text" shape="circle" icon={<BellOutlined />} size="large" />
            </Badge>
        </Dropdown>
    );
}
