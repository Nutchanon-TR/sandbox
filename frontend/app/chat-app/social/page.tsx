'use client';

import { ReloadOutlined } from '@ant-design/icons';
import { Button, Empty, Skeleton } from 'antd';
import { useRouter } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import { PersonaFeedPostCard } from '@/components/ChatApp/PersonaFeed';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { TITLE } from '@/constants/Title';
import type {
    CursorPageResponse,
    PersonaFeedFollowResponse,
    PersonaFeedPost,
} from '@/interface/ChatApp';
import { useNotification } from '@/providers/NotificationProvider';
import { useSessionStore } from '@/stores/sessionStore';
import { fetchApi } from '@/utils/api';
import { useChangeTitle } from '@/utils/breadCrumbUtil';
import { useChangeSubSideBar } from '@/utils/subSideBarUtil';

export default function PersonaFeedPage() {
    const router = useRouter();
    const notification = useNotification();
    const status = useSessionStore((state) => state.status);
    const currentUserId = useSessionStore((state) => state.internalUserId);

    const [posts, setPosts] = useState<PersonaFeedPost[]>([]);
    const [initialLoaded, setInitialLoaded] = useState(false);
    const [loading, setLoading] = useState(false);
    const [loadingMore, setLoadingMore] = useState(false);
    const [hasMore, setHasMore] = useState(false);
    const [cursor, setCursor] = useState<number | null>(null);
    const [followingPersonaId, setFollowingPersonaId] = useState<number | null>(null);

    useChangeTitle(TITLE.CHAT_APP, 'SOCIAL');
    useChangeSubSideBar(null);

    const loadInitial = useCallback(async () => {
        if (currentUserId === null) return;

        setLoading(true);
        try {
            const page = await fetchApi<CursorPageResponse<PersonaFeedPost>>(API_SANDBOX.PERSONA_FEED_POSTS, {
                limit: 20,
            });
            setPosts(page.items);
            setHasMore(page.hasMore);
            setCursor(page.nextCursor);
        } catch (error) {
            console.error(error);
            notification.error({ message: 'Failed to load PersonaFeed' });
        } finally {
            setLoading(false);
            setInitialLoaded(true);
        }
    }, [currentUserId, notification]);

    useEffect(() => {
        void loadInitial();
    }, [loadInitial]);

    const loadMore = async () => {
        if (!hasMore || cursor === null || loadingMore) return;
        setLoadingMore(true);
        try {
            const page = await fetchApi<CursorPageResponse<PersonaFeedPost>>(API_SANDBOX.PERSONA_FEED_POSTS, {
                beforeId: cursor,
                limit: 20,
            });
            setPosts((current) => [...current, ...page.items]);
            setHasMore(page.hasMore);
            setCursor(page.nextCursor);
        } catch (error) {
            console.error(error);
            notification.error({ message: 'Failed to load more posts' });
        } finally {
            setLoadingMore(false);
        }
    };

    const handleFollow = async (personaId: number) => {
        setFollowingPersonaId(personaId);
        try {
            const response = await fetchApi<PersonaFeedFollowResponse>(
                API_SANDBOX.PERSONA_FEED_FOLLOW,
                {},
                { personaId },
            );
            setPosts((current) => current.map((post) => (
                post.persona.id === personaId ? { ...post, followedByMe: true } : post
            )));
            router.push(`/chat-app/message?roomId=${response.roomId}`);
        } catch (error) {
            console.error(error);
            notification.error({ message: 'Failed to open persona chat' });
        } finally {
            setFollowingPersonaId(null);
        }
    };

    if (status === 'unauthenticated') {
        return (
            <div className="mx-auto flex h-full max-w-md items-center justify-center p-8 text-center">
                <Empty description="Please log in to use PersonaFeed." />
            </div>
        );
    }

    return (
        <div className="mx-auto flex w-full max-w-3xl flex-col gap-4 pb-12">
            <header className="sticky top-0 z-10 flex items-center justify-between gap-4 border-b border-slate-200 bg-background/95 px-1 py-3 backdrop-blur dark:border-border-main">
                <h2 className="m-0 text-xl font-semibold text-foreground">PersonaFeed</h2>
                <Button
                    type="text"
                    icon={<ReloadOutlined />}
                    loading={loading && initialLoaded}
                    title="Refresh"
                    onClick={loadInitial}
                />
            </header>

            {!initialLoaded ? (
                <div className="grid gap-4">
                    {Array.from({ length: 3 }).map((_, index) => (
                        <div key={index} className="rounded-lg border border-slate-200 bg-surface p-5 dark:border-border-main">
                            <Skeleton avatar active paragraph={{ rows: 3 }} />
                        </div>
                    ))}
                </div>
            ) : posts.length === 0 ? (
                <div className="rounded-lg border border-slate-200 bg-surface px-6 py-14 dark:border-border-main">
                    <Empty description="No PersonaFeed posts yet." />
                </div>
            ) : (
                <div className="grid gap-4">
                    {posts.map((post) => (
                        <PersonaFeedPostCard
                            key={post.id}
                            post={post}
                            following={post.followedByMe}
                            followingBusy={followingPersonaId === post.persona.id}
                            onFollow={handleFollow}
                        />
                    ))}
                </div>
            )}

            {hasMore && initialLoaded && (
                <div className="flex justify-center pt-2">
                    <Button onClick={loadMore} loading={loadingMore} disabled={loadingMore}>
                        Load more
                    </Button>
                </div>
            )}
        </div>
    );
}
