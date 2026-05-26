'use client';

import { ArrowLeftOutlined, ExperimentOutlined, MessageOutlined, UserAddOutlined } from '@ant-design/icons';
import { Avatar, Button, Empty, Skeleton, Typography } from 'antd';
import { useParams, useRouter } from 'next/navigation';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { PersonaFeedPostCard } from '@/components/ChatApp/PersonaFeed';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { TITLE } from '@/constants/Title';
import type {
    CursorPageResponse,
    PersonaFeedFollowResponse,
    PersonaFeedPersonaProfile,
    PersonaFeedPost,
} from '@/interface/ChatApp';
import { useNotification } from '@/providers/NotificationProvider';
import { useSessionStore } from '@/stores/sessionStore';
import { fetchApi } from '@/utils/api';
import { useChangeTitle } from '@/utils/breadCrumbUtil';
import { useChangeSubSideBar } from '@/utils/subSideBarUtil';

export default function PersonaProfilePage() {
    const router = useRouter();
    const params = useParams<{ personaId: string }>();
    const notification = useNotification();
    const status = useSessionStore((state) => state.status);
    const currentUserId = useSessionStore((state) => state.internalUserId);
    const personaId = useMemo(() => Number(params.personaId), [params.personaId]);

    const [profile, setProfile] = useState<PersonaFeedPersonaProfile | null>(null);
    const [posts, setPosts] = useState<PersonaFeedPost[]>([]);
    const [loading, setLoading] = useState(true);
    const [loadingMore, setLoadingMore] = useState(false);
    const [following, setFollowing] = useState(false);
    const [devPublishing, setDevPublishing] = useState(false);
    const [hasMore, setHasMore] = useState(false);
    const [cursor, setCursor] = useState<number | null>(null);

    useChangeTitle(TITLE.CHAT_APP, 'SOCIAL');
    useChangeSubSideBar(null);

    const loadProfile = useCallback(async () => {
        if (currentUserId === null || !Number.isFinite(personaId)) return;

        setLoading(true);
        try {
            const [nextProfile, page] = await Promise.all([
                fetchApi<PersonaFeedPersonaProfile>(
                    API_SANDBOX.PERSONA_FEED_PROFILE,
                    {},
                    { personaId },
                ),
                fetchApi<CursorPageResponse<PersonaFeedPost>>(
                    API_SANDBOX.PERSONA_FEED_PROFILE_POSTS,
                    { limit: 20 },
                    { personaId },
                ),
            ]);
            setProfile(nextProfile);
            setPosts(page.items);
            setHasMore(page.hasMore);
            setCursor(page.nextCursor);
        } catch (error) {
            console.error(error);
            notification.error({ message: 'Failed to load persona profile' });
        } finally {
            setLoading(false);
        }
    }, [currentUserId, notification, personaId]);

    useEffect(() => {
        void loadProfile();
    }, [loadProfile]);

    const handleFollow = async () => {
        if (!profile) return;

        setFollowing(true);
        try {
            const response = await fetchApi<PersonaFeedFollowResponse>(
                API_SANDBOX.PERSONA_FEED_FOLLOW,
                {},
                { personaId: profile.id },
            );
            setProfile((current) => current ? { ...current, followedByMe: true } : current);
            setPosts((current) => current.map((post) => ({ ...post, followedByMe: true })));
            router.push(`/chat-app/message?roomId=${response.roomId}`);
        } catch (error) {
            console.error(error);
            notification.error({ message: 'Failed to open persona chat' });
        } finally {
            setFollowing(false);
        }
    };

    const loadMore = async () => {
        if (!profile || cursor === null || !hasMore || loadingMore) return;

        setLoadingMore(true);
        try {
            const page = await fetchApi<CursorPageResponse<PersonaFeedPost>>(
                API_SANDBOX.PERSONA_FEED_PROFILE_POSTS,
                { beforeId: cursor, limit: 20 },
                { personaId: profile.id },
            );
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

    const handleDevPublishNow = async () => {
        if (!profile) return;

        setDevPublishing(true);
        try {
            await fetchApi<void>(
                API_SANDBOX.PERSONA_FEED_DEV_PUBLISH_NOW,
                {},
                { personaId: profile.id },
            );
            notification.success({ message: 'Dev post triggered' });
            await loadProfile();
        } catch (error) {
            console.error(error);
            notification.error({ message: 'Failed to trigger dev post' });
        } finally {
            setDevPublishing(false);
        }
    };

    if (status === 'unauthenticated') {
        return (
            <div className="mx-auto flex h-full max-w-md items-center justify-center p-8 text-center">
                <Empty description="Please log in to use PersonaFeed." />
            </div>
        );
    }

    if (loading) {
        return (
            <div className="mx-auto flex w-full max-w-3xl flex-col gap-4 pb-12">
                <div className="rounded-lg border border-slate-200 bg-surface p-5 dark:border-border-main">
                    <Skeleton avatar active paragraph={{ rows: 3 }} />
                </div>
                <div className="rounded-lg border border-slate-200 bg-surface p-5 dark:border-border-main">
                    <Skeleton active paragraph={{ rows: 4 }} />
                </div>
            </div>
        );
    }

    if (!profile) {
        return (
            <div className="mx-auto flex w-full max-w-3xl flex-col gap-4 pb-12">
                <Button className="w-fit" icon={<ArrowLeftOutlined />} onClick={() => router.push('/chat-app/persona')}>
                    PersonaFeed
                </Button>
                <div className="rounded-lg border border-slate-200 bg-surface px-6 py-14 dark:border-border-main">
                    <Empty description="Persona not found." />
                </div>
            </div>
        );
    }

    return (
        <div className="mx-auto flex w-full max-w-3xl flex-col gap-4 pb-12">
            <Button className="w-fit" type="text" icon={<ArrowLeftOutlined />} onClick={() => router.push('/chat-app/persona')}>
                PersonaFeed
            </Button>

            <section className="overflow-hidden rounded-lg border border-slate-200 bg-surface shadow-sm dark:border-border-main">
                <div
                    className="h-36 bg-slate-200 bg-cover bg-center dark:bg-slate-800"
                    style={profile.posterUrl ? { backgroundImage: `url(${profile.posterUrl})` } : undefined}
                />
                <div className="flex flex-col gap-4 p-4 md:flex-row md:items-end md:justify-between md:p-5">
                    <div className="flex min-w-0 items-end gap-3">
                        <Avatar src={profile.avatarUrl ?? undefined} size={72}>
                            {profile.aiName?.[0]}
                        </Avatar>
                        <div className="min-w-0 pb-1">
                            <Typography.Title level={3} className="!mb-1 truncate !text-foreground">
                                {profile.aiName}
                            </Typography.Title>
                            {profile.role && (
                                <Typography.Text type="secondary" className="line-clamp-2 text-sm">
                                    {profile.role}
                                </Typography.Text>
                            )}
                        </div>
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                        {process.env.NEXT_PUBLIC_PERSONA_FEED_DEV_ACTIONS_ENABLED === 'true' && (
                            <Button
                                type="dashed"
                                icon={<ExperimentOutlined />}
                                loading={devPublishing}
                                onClick={handleDevPublishNow}
                            >
                                Dev post
                            </Button>
                        )}
                        <Button
                            type={profile.followedByMe ? 'default' : 'primary'}
                            icon={profile.followedByMe ? <MessageOutlined /> : <UserAddOutlined />}
                            loading={following}
                            onClick={handleFollow}
                        >
                            {profile.followedByMe ? 'Open chat' : 'Add'}
                        </Button>
                    </div>
                </div>
                {profile.biography && (
                    <p className="m-0 border-t border-slate-200 px-4 py-4 text-sm leading-6 text-foreground dark:border-border-main md:px-5">
                        {profile.biography}
                    </p>
                )}
            </section>

            {posts.length === 0 ? (
                <div className="rounded-lg border border-slate-200 bg-surface px-6 py-14 dark:border-border-main">
                    <Empty description="No posts yet." />
                </div>
            ) : (
                <div className="grid gap-4">
                    {posts.map((post) => (
                        <PersonaFeedPostCard
                            key={post.id}
                            post={post}
                            following={profile.followedByMe || post.followedByMe}
                            followingBusy={following}
                            onFollow={handleFollow}
                            onDevPublishNow={handleDevPublishNow}
                            devPublishing={devPublishing}
                        />
                    ))}
                </div>
            )}

            {hasMore && (
                <div className="flex justify-center pt-2">
                    <Button onClick={loadMore} loading={loadingMore} disabled={loadingMore}>
                        Load more
                    </Button>
                </div>
            )}
        </div>
    );
}
