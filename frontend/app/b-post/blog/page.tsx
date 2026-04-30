'use client';

import { Empty, Spin } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { TITLE } from '@/constants/Title';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { fetchApi } from '@/utils/api';
import { useChangeTitle } from '@/utils/breadCrumbUtil';
import { useSessionStore } from '@/stores/sessionStore';
import { PageResponse, PostDto } from '@/interface/BPost';
import { PostCard } from '@/components/Bpost/PostCard';
import { PostComposer } from '@/components/Bpost/PostComposer';
import { NotificationBell } from '@/components/Bpost/NotificationBell';

export default function BlogPage() {
    useChangeTitle(TITLE.B_POST, 'BLOG');
    const status = useSessionStore((s) => s.status);
    const internalUserId = useSessionStore((s) => s.internalUserId);

    const [posts, setPosts] = useState<PostDto[]>([]);
    const [loading, setLoading] = useState(false);
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
        }
    }, []);

    useEffect(() => {
        if (internalUserId != null) loadInitial();
    }, [internalUserId, loadInitial]);

    const loadMore = async () => {
        if (!hasMore || cursor == null || loading) return;
        setLoading(true);
        try {
            const res = await fetchApi<PageResponse<PostDto>>(API_SANDBOX.B_POST_POST_FEED, { beforeId: cursor, limit: 20 });
            setPosts((prev) => [...prev, ...res.items]);
            setHasMore(res.hasMore);
            setCursor(res.nextCursor);
        } finally {
            setLoading(false);
        }
    };

    if (status === 'unauthenticated') {
        return <div className="p-8 text-center text-text-secondary">Please log in to use the blog.</div>;
    }

    return (
        <div className="mx-auto flex w-full max-w-3xl flex-col gap-4 pb-12">
            <div className="flex items-center justify-between">
                <h2 className="m-0 text-xl font-semibold">Feed</h2>
                <NotificationBell />
            </div>

            <PostComposer onCreated={(p) => setPosts((prev) => [p, ...prev])} />

            {loading && posts.length === 0 ? (
                <div className="flex justify-center py-12"><Spin size="large" /></div>
            ) : posts.length === 0 ? (
                <Empty description="No posts yet — add a friend or write the first post." />
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

            {hasMore && (
                <button
                    type="button"
                    className="mx-auto mt-2 rounded-full border bg-surface px-4 py-2 text-sm hover:bg-muted"
                    onClick={loadMore}
                    disabled={loading}
                >
                    {loading ? 'Loading…' : 'Load more'}
                </button>
            )}
        </div>
    );
}
