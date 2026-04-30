'use client';

import { Button } from 'antd';
import { CheckOutlined, ClockCircleOutlined, UserAddOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { fetchApi } from '@/utils/api';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { FriendshipDto } from '@/interface/BPost';

type State = 'NOT_FRIEND' | 'REQUESTED' | 'INCOMING' | 'FRIENDS';

export function FriendButton({
    targetUserId,
    initialState,
    pendingRequestId,
    onChange,
}: {
    targetUserId: number;
    initialState: State;
    pendingRequestId?: number;
    onChange?: (state: State) => void;
}) {
    const [state, setState] = useState<State>(initialState);
    const [busy, setBusy] = useState(false);

    const send = async () => {
        setBusy(true);
        try {
            await fetchApi<FriendshipDto>(API_SANDBOX.B_POST_FRIEND_REQUEST_SEND, { addresseeId: targetUserId });
            setState('REQUESTED');
            onChange?.('REQUESTED');
        } finally {
            setBusy(false);
        }
    };

    const accept = async () => {
        if (!pendingRequestId) return;
        setBusy(true);
        try {
            await fetchApi(API_SANDBOX.B_POST_FRIEND_REQUEST_ACCEPT, {}, { id: pendingRequestId });
            setState('FRIENDS');
            onChange?.('FRIENDS');
        } finally {
            setBusy(false);
        }
    };

    if (state === 'FRIENDS') {
        return <Button icon={<CheckOutlined />} disabled>Friends</Button>;
    }
    if (state === 'REQUESTED') {
        return <Button icon={<ClockCircleOutlined />} disabled>Requested</Button>;
    }
    if (state === 'INCOMING') {
        return (
            <Button type="primary" icon={<CheckOutlined />} loading={busy} onClick={accept}>
                Accept
            </Button>
        );
    }
    return (
        <Button type="primary" icon={<UserAddOutlined />} loading={busy} onClick={send}>
            Add Friend
        </Button>
    );
}
