'use client';

import { Button, Empty, Skeleton, Spin } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useCallback, useEffect, useState } from 'react';
import { TITLE } from '@/constants/Title';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { fetchApi } from '@/utils/api';
import { useChangeTitle } from '@/utils/breadCrumbUtil';
import { useSessionStore } from '@/stores/sessionStore';
import { useLoadingContext } from '@/providers/LoadingBarProvider';
import { PageResponse, PostDto } from '@/interface/BPost';
import { PostCard } from '@/components/Bpost/PostCard';
import { PostComposer } from '@/components/Bpost/PostComposer';
import { NotificationBell } from '@/components/Bpost/NotificationBell';

const BORDER = 'border-slate-300 dark:border-border-main';

export default function BlogPage() {
    useChangeTitle(TITLE.B_POST, 'BLOG');
    const status = useSessionStore((s) => s.status);
    const internalUserId = useSessionStore((s) => s.internalUserId);
    const { setIsLoading } = useLoadingContext();

    const [posts, setPosts] = useState<PostDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [loadingMore, setLoadingMore] = useState(false);
    const [initialLoaded, setInitialLoaded] = useState(false);
    const [hasMore, setHasMore] = useState(false);
    const [cursor, setCursor] = useState<number | null>(null);

    const loadInitial = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetchApi<PageResponse<PostDto>>(API_SANDBOX.B_POST_POST_FEED, { limit: 20 });
            setPosts(res.items);
            setHasMore(res.hasMore);
            setCursor(res.nextCursor);
        } finally {
            setLoading(false);
            setInitialLoaded(true);
        }
    }, []);

    useEffect(() => {
        if (internalUserId != null) loadInitial();
    }, [internalUserId, loadInitial]);

    // Drive global loading overlay until the first feed page is fetched
    useEffect(() => {
        const blocking = internalUserId === null || (!initialLoaded && loading);
        setIsLoading(blocking);
        return () => setIsLoading(false);
    }, [internalUserId, initialLoaded, loading, setIsLoading]);

    const loadMore = async () => {
        if (!hasMore || cursor == null || loadingMore) return;
        setLoadingMore(true);
        try {
            const res = await fetchApi<PageResponse<PostDto>>(API_SANDBOX.B_POST_POST_FEED, { beforeId: cursor, limit: 20 });
            setPosts((prev) => [...prev, ...res.items]);
            setHasMore(res.hasMore);
            setCursor(res.nextCursor);
        } finally {
            setLoadingMore(false);
        }
    };

    if (status === 'unauthenticated') {
        return (
            <div className="mx-auto flex h-full max-w-md items-center justify-center p-8 text-center">
                <Empty description="Please log in to use the blog." />
            </div>
        );
    }

    return (
        <div className="mx-auto flex w-full max-w-3xl flex-col gap-5 pb-12">
            <div className={`sticky top-0 z-10 flex items-center justify-between rounded-2xl border bg-surface/90 px-5 py-3 shadow-sm backdrop-blur supports-[backdrop-filter]:bg-surface/70 ${BORDER}`}>
                <div className="flex flex-col">
                    <h2 className="m-0 text-xl font-semibold leading-none">Feed</h2>
                    <span className="mt-1 text-xs text-text-secondary">
                        Latest posts from you and your friends
                    </span>
                </div>
                <div className="flex items-center gap-1">
                    <Button
                        type="text"
                        icon={<ReloadOutlined />}
                        onClick={loadInitial}
                        loading={loading && initialLoaded}
                        title="Refresh"
                    />
                    <NotificationBell />
                </div>
            </div>

            <PostComposer onCreated={(p) => setPosts((prev) => [p, ...prev])} />

            {!initialLoaded ? (
                <div className="flex flex-col gap-4">
                    {Array.from({ length: 3 }).map((_, i) => (
                        <div key={i} className={`rounded-2xl border bg-surface p-5 ${BORDER}`}>
                            <Skeleton avatar active paragraph={{ rows: 3 }} />
                        </div>
                    ))}
                </div>
            ) : posts.length === 0 ? (
                <div className={`rounded-2xl border bg-surface px-6 py-12 ${BORDER}`}>
                    <Empty description="No posts yet — add a friend or write the first post." />
                </div>
            ) : (
                <div className="flex flex-col gap-4">
                    {posts.map((post) => (
                        <PostCard
                            key={post.id}
                            post={post}
                            onChange={(next) => setPosts((prev) => prev.map((p) => (p.id === next.id ? next : p)))}
                            onDelete={(id) => setPosts((prev) => prev.filter((p) => p.id !== id))}
                        />
                    ))}
                </div>
            )}

            {hasMore && initialLoaded && (
                <div className="mt-2 flex justify-center">
                    <Button
                        shape="round"
                        size="large"
                        onClick={loadMore}
                        loading={loadingMore}
                        disabled={loadingMore}
                    >
                        {loadingMore ? 'Loading…' : 'Load more posts'}
                    </Button>
                </div>
            )}

            {loadingMore && !hasMore && (
                <div className="flex justify-center py-4">
                    <Spin />
                </div>
            )}
        </div>
    );
}
