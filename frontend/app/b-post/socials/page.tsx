'use client';

import { Avatar, Button, Card, Empty, Input, List, Space, Tabs, Typography } from 'antd';
import { CheckOutlined, CloseOutlined, MessageOutlined, SearchOutlined } from '@ant-design/icons';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { TITLE } from '@/constants/Title';
import { useChangeTitle } from '@/utils/breadCrumbUtil';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { fetchApi } from '@/utils/api';
import { useSessionStore } from '@/stores/sessionStore';
import { FriendshipDto, UserSummary } from '@/interface/BPost';
import { PresenceDot } from '@/components/Bpost/PresenceDot';

const { Text } = Typography;

export default function SocialsPage() {
    useChangeTitle(TITLE.B_POST, 'SOCIALS');
    const me = useSessionStore((s) => s.internalUserId);
    const router = useRouter();

    const [friends, setFriends] = useState<UserSummary[]>([]);
    const [incoming, setIncoming] = useState<FriendshipDto[]>([]);
    const [outgoing, setOutgoing] = useState<FriendshipDto[]>([]);

    const [searchQ, setSearchQ] = useState('');
    const [searchResults, setSearchResults] = useState<UserSummary[]>([]);
    const [searching, setSearching] = useState(false);

    const friendIds = useMemo(() => new Set(friends.map((f) => f.id)), [friends]);
    const outgoingIds = useMemo(() => new Set(outgoing.map((f) => f.addressee.id)), [outgoing]);

    const loadAll = useCallback(async () => {
        const [fr, inc, out] = await Promise.all([
            fetchApi<UserSummary[]>(API_SANDBOX.B_POST_FRIEND_LIST),
            fetchApi<FriendshipDto[]>(API_SANDBOX.B_POST_FRIEND_REQUESTS_INCOMING),
            fetchApi<FriendshipDto[]>(API_SANDBOX.B_POST_FRIEND_REQUESTS_OUTGOING),
        ]);
        setFriends(fr);
        setIncoming(inc);
        setOutgoing(out);
    }, []);

    useEffect(() => {
        if (me != null) loadAll();
    }, [me, loadAll]);

    const handleSearch = async (q: string) => {
        const trimmed = q.trim();
        if (!trimmed) {
            setSearchResults([]);
            return;
        }
        setSearching(true);
        try {
            setSearchResults(await fetchApi<UserSummary[]>(API_SANDBOX.B_POST_USER_SEARCH, { q: trimmed }));
        } finally {
            setSearching(false);
        }
    };

    const sendRequest = async (userId: number) => {
        await fetchApi(API_SANDBOX.B_POST_FRIEND_REQUEST_SEND, { addresseeId: userId });
        loadAll();
    };

    const respond = async (id: number, accept: boolean) => {
        await fetchApi(
            accept ? API_SANDBOX.B_POST_FRIEND_REQUEST_ACCEPT : API_SANDBOX.B_POST_FRIEND_REQUEST_DECLINE,
            {},
            { id }
        );
        loadAll();
    };

    const openChat = async (userId: number) => {
        const { id } = await fetchApi<{ id: number }>(API_SANDBOX.B_POST_CONVERSATION_OPEN, { userId });
        router.push(`/b-post/messages?conversationId=${id}`);
    };

    return (
        <div className="mx-auto w-full max-w-4xl pb-12">
            <Tabs
                defaultActiveKey="friends"
                items={[
                    {
                        key: 'friends',
                        label: `Friends (${friends.length})`,
                        children: (
                            <List
                                grid={{ gutter: 16, column: 2, xs: 1, sm: 2 }}
                                dataSource={friends}
                                locale={{ emptyText: <Empty description="No friends yet" /> }}
                                renderItem={(u) => (
                                    <List.Item>
                                        <Card className="rounded-2xl">
                                            <div className="flex items-center justify-between">
                                                <Space>
                                                    <div className="relative">
                                                        <Avatar src={u.avatarUrl ?? undefined} size={42}>
                                                            {u.displayName?.[0]}
                                                        </Avatar>
                                                        <div className="absolute -bottom-0.5 -right-0.5">
                                                            <PresenceDot userId={u.id} />
                                                        </div>
                                                    </div>
                                                    <Text strong>{u.displayName}</Text>
                                                </Space>
                                                <Button icon={<MessageOutlined />} onClick={() => openChat(u.id)}>
                                                    Chat
                                                </Button>
                                            </div>
                                        </Card>
                                    </List.Item>
                                )}
                            />
                        ),
                    },
                    {
                        key: 'requests',
                        label: `Requests (${incoming.length})`,
                        children: (
                            <List
                                dataSource={incoming}
                                locale={{ emptyText: <Empty description="No incoming requests" /> }}
                                renderItem={(f) => (
                                    <List.Item
                                        actions={[
                                            <Button key="a" type="primary" icon={<CheckOutlined />} onClick={() => respond(f.id, true)}>
                                                Accept
                                            </Button>,
                                            <Button key="d" icon={<CloseOutlined />} onClick={() => respond(f.id, false)}>
                                                Decline
                                            </Button>,
                                        ]}
                                    >
                                        <List.Item.Meta
                                            avatar={<Avatar src={f.requester?.avatarUrl ?? undefined}>{f.requester?.displayName?.[0]}</Avatar>}
                                            title={f.requester?.displayName}
                                            description="wants to be friends"
                                        />
                                    </List.Item>
                                )}
                            />
                        ),
                    },
                    {
                        key: 'find',
                        label: 'Find People',
                        children: (
                            <div>
                                <Input.Search
                                    enterButton={<SearchOutlined />}
                                    placeholder="Search by name…"
                                    value={searchQ}
                                    onChange={(e) => setSearchQ(e.target.value)}
                                    onSearch={handleSearch}
                                    loading={searching}
                                    className="!mb-4"
                                />
                                <List
                                    dataSource={searchResults}
                                    locale={{ emptyText: <Empty description="No results" /> }}
                                    renderItem={(u) => {
                                        const alreadyFriend = friendIds.has(u.id);
                                        const requested = outgoingIds.has(u.id);
                                        return (
                                            <List.Item
                                                actions={[
                                                    alreadyFriend ? (
                                                        <Button key="f" disabled>Friends</Button>
                                                    ) : requested ? (
                                                        <Button key="r" disabled>Requested</Button>
                                                    ) : (
                                                        <Button key="s" type="primary" onClick={() => sendRequest(u.id)}>
                                                            Add friend
                                                        </Button>
                                                    ),
                                                ]}
                                            >
                                                <List.Item.Meta
                                                    avatar={<Avatar src={u.avatarUrl ?? undefined}>{u.displayName?.[0]}</Avatar>}
                                                    title={u.displayName}
                                                />
                                            </List.Item>
                                        );
                                    }}
                                />
                            </div>
                        ),
                    },
                ]}
            />
        </div>
    );
}
