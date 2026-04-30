'use client';

import { Avatar, Button, Empty, Spin, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { TITLE } from '@/constants/Title';
import { useChangeTitle } from '@/utils/breadCrumbUtil';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { fetchApi } from '@/utils/api';
import { useSessionStore } from '@/stores/sessionStore';
import { FriendshipDto, PageResponse, PostDto, UserSummary } from '@/interface/BPost';
import { PostCard } from '@/components/Bpost/PostCard';
import { FriendButton } from '@/components/Bpost/FriendButton';
import { PresenceDot } from '@/components/Bpost/PresenceDot';
import { formatDateTime } from '@/utils/time';

const { Text } = Typography;

export default function ProfilePage() {
    useChangeTitle(TITLE.B_POST, 'BLOG');
    const params = useParams<{ supabaseUid: string }>();
    const router = useRouter();
    const me = useSessionStore((s) => s.internalUserId);

    const [user, setUser] = useState<UserSummary | null>(null);
    const [posts, setPosts] = useState<PostDto[]>([]);
    const [friends, setFriends] = useState<UserSummary[]>([]);
    const [incoming, setIncoming] = useState<FriendshipDto[]>([]);
    const [outgoing, setOutgoing] = useState<FriendshipDto[]>([]);
    const [loading, setLoading] = useState(false);

    const load = useCallback(async () => {
        if (!params?.supabaseUid) return;
        setLoading(true);
        try {
            const found = await fetchApi<UserSummary[]>(API_SANDBOX.B_POST_USER_SEARCH, { q: '' }).catch(() => [] as UserSummary[]);
            // search by uid not directly supported — fallback: iterate friends + try resolving via friends list / search results.
            // The realistic flow: derive from friends list / explicit search.
            let resolved = found.find((u) => u.supabaseUid === params.supabaseUid) ?? null;

            const [friendList, incomingList, outgoingList] = await Promise.all([
                fetchApi<UserSummary[]>(API_SANDBOX.B_POST_FRIEND_LIST),
                fetchApi<FriendshipDto[]>(API_SANDBOX.B_POST_FRIEND_REQUESTS_INCOMING),
                fetchApi<FriendshipDto[]>(API_SANDBOX.B_POST_FRIEND_REQUESTS_OUTGOING),
            ]);
            setFriends(friendList);
            setIncoming(incomingList);
            setOutgoing(outgoingList);

            if (!resolved) {
                resolved = friendList.find((u) => u.supabaseUid === params.supabaseUid)
                    ?? incomingList.map((i) => i.requester).find((u) => u.supabaseUid === params.supabaseUid)
                    ?? outgoingList.map((o) => o.addressee).find((u) => u.supabaseUid === params.supabaseUid)
                    ?? null;
            }
            setUser(resolved);

            if (resolved) {
                const feed = await fetchApi<PageResponse<PostDto>>(
                    API_SANDBOX.B_POST_POST_BY_AUTHOR,
                    { limit: 20 },
                    { authorId: resolved.id }
                );
                setPosts(feed.items);
            }
        } finally {
            setLoading(false);
        }
    }, [params?.supabaseUid]);

    useEffect(() => {
        if (me != null) load();
    }, [me, load]);

    if (loading) {
        return <div className="flex justify-center py-12"><Spin size="large" /></div>;
    }
    if (!user) {
        return <Empty description="User not found in your friend network." />;
    }

    const isMe = user.id === me;
    const isFriend = friends.some((f) => f.id === user.id);
    const incomingReq = incoming.find((f) => f.requester.id === user.id);
    const requested = outgoing.some((f) => f.addressee.id === user.id);
    const friendState: 'NOT_FRIEND' | 'REQUESTED' | 'INCOMING' | 'FRIENDS' =
        isFriend ? 'FRIENDS' : incomingReq ? 'INCOMING' : requested ? 'REQUESTED' : 'NOT_FRIEND';

    const openChat = async () => {
        const { id } = await fetchApi<{ id: number }>(API_SANDBOX.B_POST_CONVERSATION_OPEN, { userId: user.id });
        router.push(`/b-post/messages?conversationId=${id}`);
    };

    return (
        <div className="mx-auto w-full max-w-3xl pb-12">
            <div className="rounded-3xl border bg-surface p-6 shadow-sm">
                <div className="flex items-start gap-4">
                    <div className="relative">
                        <Avatar src={user.avatarUrl ?? undefined} size={80}>{user.displayName?.[0]}</Avatar>
                        <div className="absolute bottom-0 right-0">
                            <PresenceDot userId={user.id} size={14} />
                        </div>
                    </div>
                    <div className="flex-1">
                        <h2 className="m-0 text-xl font-semibold">{user.displayName}</h2>
                        <Text type="secondary">{friends.length} friends</Text>
                        {user.lastSeenAt && !isMe && (
                            <div className="text-xs text-text-secondary">Last seen {formatDateTime(user.lastSeenAt)}</div>
                        )}
                    </div>
                    {!isMe && (
                        <div className="flex gap-2">
                            <FriendButton
                                targetUserId={user.id}
                                initialState={friendState}
                                pendingRequestId={incomingReq?.id}
                                onChange={() => load()}
                            />
                            {isFriend && <Button onClick={openChat}>Message</Button>}
                        </div>
                    )}
                </div>
            </div>

            <h3 className="mt-6 mb-3 text-lg font-semibold">Posts</h3>
            <div className="flex flex-col gap-4">
                {posts.length === 0 ? (
                    <Empty description="No posts yet" />
                ) : (
                    posts.map((post) => (
                        <PostCard
                            key={post.id}
                            post={post}
                            onChange={(next) => setPosts((prev) => prev.map((p) => (p.id === next.id ? next : p)))}
                            onDelete={(id) => setPosts((prev) => prev.filter((p) => p.id !== id))}
                        />
                    ))
                )}
            </div>
        </div>
    );
}
