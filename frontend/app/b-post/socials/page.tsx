'use client';

import { Avatar, Badge, Button, Card, Empty, Input, List, Skeleton, Space, Tabs, Tag, Typography } from 'antd';
import {
    CheckOutlined,
    CloseOutlined,
    MessageOutlined,
    SearchOutlined,
    TeamOutlined,
    UserAddOutlined,
    UsergroupAddOutlined,
} from '@ant-design/icons';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { TITLE } from '@/constants/Title';
import { useChangeTitle } from '@/utils/breadCrumbUtil';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { fetchApi } from '@/utils/api';
import { useSessionStore } from '@/stores/sessionStore';
import { useLoadingContext } from '@/providers/LoadingBarProvider';
import { FriendshipDto, UserSummary } from '@/interface/BPost';
import { PresenceDot } from '@/components/Bpost/PresenceDot';

const { Text, Title } = Typography;

const BORDER = 'border-slate-300 dark:border-border-main';

export default function SocialsPage() {
    useChangeTitle(TITLE.B_POST, 'SOCIALS');
    const me = useSessionStore((s) => s.internalUserId);
    const router = useRouter();
    const { setIsLoading } = useLoadingContext();

    const [friends, setFriends] = useState<UserSummary[]>([]);
    const [incoming, setIncoming] = useState<FriendshipDto[]>([]);
    const [outgoing, setOutgoing] = useState<FriendshipDto[]>([]);
    const [loadingAll, setLoadingAll] = useState(false);
    const [initialLoaded, setInitialLoaded] = useState(false);

    const [searchQ, setSearchQ] = useState('');
    const [searchResults, setSearchResults] = useState<UserSummary[]>([]);
    const [searched, setSearched] = useState(false);
    const [searching, setSearching] = useState(false);

    const friendIds = useMemo(() => new Set(friends.map((f) => f.id)), [friends]);
    const outgoingIds = useMemo(() => new Set(outgoing.map((f) => f.addressee.id)), [outgoing]);

    const loadAll = useCallback(async () => {
        setLoadingAll(true);
        try {
            const [fr, inc, out] = await Promise.all([
                fetchApi<UserSummary[]>(API_SANDBOX.B_POST_FRIEND_LIST),
                fetchApi<FriendshipDto[]>(API_SANDBOX.B_POST_FRIEND_REQUESTS_INCOMING),
                fetchApi<FriendshipDto[]>(API_SANDBOX.B_POST_FRIEND_REQUESTS_OUTGOING),
            ]);
            setFriends(fr);
            setIncoming(inc);
            setOutgoing(out);
        } finally {
            setLoadingAll(false);
            setInitialLoaded(true);
        }
    }, []);

    useEffect(() => {
        if (me != null) loadAll();
    }, [me, loadAll]);

    // Drive global loading overlay until the first social fetch completes
    useEffect(() => {
        const blocking = me === null || (!initialLoaded && loadingAll);
        setIsLoading(blocking);
        return () => setIsLoading(false);
    }, [me, initialLoaded, loadingAll, setIsLoading]);

    const handleSearch = async (q: string) => {
        const trimmed = q.trim();
        if (!trimmed) {
            setSearchResults([]);
            setSearched(false);
            return;
        }
        setSearching(true);
        try {
            setSearchResults(await fetchApi<UserSummary[]>(API_SANDBOX.B_POST_USER_SEARCH, { q: trimmed }));
            setSearched(true);
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

    const friendsTab = !initialLoaded ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {Array.from({ length: 4 }).map((_, i) => (
                <Card key={i} className={`!rounded-2xl border bg-surface ${BORDER}`}>
                    <Skeleton avatar active paragraph={{ rows: 1 }} />
                </Card>
            ))}
        </div>
    ) : (
        <List
            grid={{ gutter: 16, column: 2, xs: 1, sm: 2 }}
            dataSource={friends}
            locale={{
                emptyText: (
                    <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description={
                            <span className="text-text-secondary">
                                No friends yet — try the <b>Find People</b> tab.
                            </span>
                        }
                    />
                ),
            }}
            renderItem={(u) => (
                <List.Item>
                    <Card
                        className={`!rounded-2xl border bg-surface transition-all hover:!border-blue-300 hover:shadow-md dark:hover:!border-blue-500/40 ${BORDER}`}
                        styles={{ body: { padding: 16 } }}
                    >
                        <div className="flex items-center justify-between gap-3">
                            <Space size="middle" className="min-w-0">
                                <div className="relative shrink-0">
                                    <Avatar src={u.avatarUrl ?? undefined} size={48}>
                                        {u.displayName?.[0]}
                                    </Avatar>
                                    <div className="absolute -bottom-0.5 -right-0.5">
                                        <PresenceDot userId={u.id} />
                                    </div>
                                </div>
                                <div className="flex min-w-0 flex-col">
                                    <Text strong className="truncate !text-foreground">
                                        {u.displayName}
                                    </Text>
                                    <Text className="truncate text-xs !text-text-secondary">Friend</Text>
                                </div>
                            </Space>
                            <Button type="primary" ghost shape="round" icon={<MessageOutlined />} onClick={() => openChat(u.id)}>
                                Chat
                            </Button>
                        </div>
                    </Card>
                </List.Item>
            )}
        />
    );

    const requestsTab = !initialLoaded ? (
        <Skeleton active avatar paragraph={{ rows: 2 }} />
    ) : (
        <List
            dataSource={incoming}
            locale={{
                emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No incoming requests" />,
            }}
            renderItem={(f) => (
                <List.Item
                    className={`!rounded-2xl !border !px-4 !py-3 mb-2 bg-surface ${BORDER}`}
                    actions={[
                        <Button
                            key="a"
                            type="primary"
                            shape="round"
                            icon={<CheckOutlined />}
                            onClick={() => respond(f.id, true)}
                        >
                            Accept
                        </Button>,
                        <Button
                            key="d"
                            shape="round"
                            icon={<CloseOutlined />}
                            onClick={() => respond(f.id, false)}
                        >
                            Decline
                        </Button>,
                    ]}
                >
                    <List.Item.Meta
                        avatar={
                            <Avatar size={44} src={f.requester?.avatarUrl ?? undefined}>
                                {f.requester?.displayName?.[0]}
                            </Avatar>
                        }
                        title={<span className="!text-foreground">{f.requester?.displayName}</span>}
                        description={<span className="text-text-secondary">wants to be friends</span>}
                    />
                </List.Item>
            )}
        />
    );

    const findTab = (
        <div className="flex flex-col gap-4">
            <Input.Search
                size="large"
                enterButton={<SearchOutlined />}
                placeholder="Search by name…"
                value={searchQ}
                onChange={(e) => setSearchQ(e.target.value)}
                onSearch={handleSearch}
                loading={searching}
                allowClear
            />
            {searching ? (
                <Skeleton active avatar paragraph={{ rows: 2 }} />
            ) : !searched ? (
                <div className={`rounded-2xl border bg-surface px-6 py-10 text-center ${BORDER}`}>
                    <UsergroupAddOutlined className="text-3xl text-text-secondary" />
                    <p className="mt-2 text-sm text-text-secondary">
                        Search by display name to discover and add new friends.
                    </p>
                </div>
            ) : (
                <List
                    dataSource={searchResults}
                    locale={{
                        emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No matches found" />,
                    }}
                    renderItem={(u) => {
                        const alreadyFriend = friendIds.has(u.id);
                        const requested = outgoingIds.has(u.id);
                        return (
                            <List.Item
                                className={`!rounded-2xl !border !px-4 !py-3 mb-2 bg-surface ${BORDER}`}
                                actions={[
                                    alreadyFriend ? (
                                        <Tag key="f" color="success">Friends</Tag>
                                    ) : requested ? (
                                        <Tag key="r" color="warning">Requested</Tag>
                                    ) : (
                                        <Button
                                            key="s"
                                            type="primary"
                                            shape="round"
                                            icon={<UserAddOutlined />}
                                            onClick={() => sendRequest(u.id)}
                                        >
                                            Add friend
                                        </Button>
                                    ),
                                ]}
                            >
                                <List.Item.Meta
                                    avatar={
                                        <Avatar size={44} src={u.avatarUrl ?? undefined}>
                                            {u.displayName?.[0]}
                                        </Avatar>
                                    }
                                    title={<span className="!text-foreground">{u.displayName}</span>}
                                />
                            </List.Item>
                        );
                    }}
                />
            )}
        </div>
    );

    return (
        <div className="mx-auto flex w-full max-w-4xl flex-col gap-4 pb-12">
            <div className={`flex items-center justify-between rounded-2xl border bg-surface px-5 py-4 shadow-sm ${BORDER}`}>
                <Space size="middle">
                    <div className="flex h-10 w-10 items-center justify-center rounded-full bg-blue-50 text-blue-600 dark:bg-blue-500/15 dark:text-blue-300">
                        <TeamOutlined className="text-lg" />
                    </div>
                    <div className="flex flex-col">
                        <Title level={5} className="!m-0 !text-foreground">
                            Social
                        </Title>
                        <Text className="text-xs !text-text-secondary">
                            Manage your friends and discover new people.
                        </Text>
                    </div>
                </Space>
                <div className="hidden items-center gap-3 md:flex">
                    <Tag color="blue" className="!rounded-full !px-3 !py-1">
                        {friends.length} friends
                    </Tag>
                    {incoming.length > 0 && (
                        <Tag color="orange" className="!rounded-full !px-3 !py-1">
                            {incoming.length} pending
                        </Tag>
                    )}
                </div>
            </div>

            <div className={`rounded-2xl border bg-surface px-3 pt-1 pb-3 shadow-sm ${BORDER}`}>
                <Tabs
                    defaultActiveKey="friends"
                    size="large"
                    items={[
                        {
                            key: 'friends',
                            label: (
                                <span className="px-1">
                                    Friends
                                    <Badge count={friends.length} showZero className="ml-2" color="blue" />
                                </span>
                            ),
                            children: friendsTab,
                        },
                        {
                            key: 'requests',
                            label: (
                                <span className="px-1">
                                    Requests
                                    <Badge count={incoming.length} showZero className="ml-2" color="orange" />
                                </span>
                            ),
                            children: requestsTab,
                        },
                        {
                            key: 'find',
                            label: <span className="px-1">Find People</span>,
                            children: findTab,
                        },
                    ]}
                />
            </div>
        </div>
    );
}
