'use client';

import { MessageOutlined, UserAddOutlined } from '@ant-design/icons';
import { Avatar, Button, Image, Typography } from 'antd';
import Link from 'next/link';
import type { PersonaFeedPost } from '@/interface/ChatApp';
import { formatDateTime, formatRelative } from '@/utils/time';

interface PersonaFeedPostCardProps {
    post: PersonaFeedPost;
    following: boolean;
    followingBusy: boolean;
    onFollow: (personaId: number) => void;
}

export function PersonaFeedPostCard({
    post,
    following,
    followingBusy,
    onFollow,
}: PersonaFeedPostCardProps) {
    return (
        <article className="overflow-hidden rounded-lg border border-slate-200 bg-surface shadow-sm dark:border-border-main">
            <div className="flex items-start justify-between gap-4 p-4 md:p-5">
                <Link
                    href={`/chat-app/social/${post.persona.id}`}
                    className="flex min-w-0 items-center gap-3 text-inherit"
                >
                    <Avatar src={post.persona.avatarUrl ?? undefined} size={44}>
                        {post.persona.aiName?.[0]}
                    </Avatar>
                    <div className="min-w-0">
                        <Typography.Text strong className="block truncate !text-foreground">
                            {post.persona.aiName}
                        </Typography.Text>
                        <Typography.Text
                            type="secondary"
                            className="block text-xs"
                            title={formatDateTime(post.createdAt)}
                        >
                            {formatRelative(post.createdAt)}
                        </Typography.Text>
                    </div>
                </Link>

                <Button
                    type={following ? 'default' : 'primary'}
                    icon={following ? <MessageOutlined /> : <UserAddOutlined />}
                    loading={followingBusy}
                    onClick={() => onFollow(post.persona.id)}
                >
                    {following ? 'Open chat' : 'Add'}
                </Button>
            </div>

            <div className="px-4 pb-4 md:px-5 md:pb-5">
                <p className="m-0 whitespace-pre-wrap text-sm leading-6 text-foreground">{post.content}</p>

                {post.imageUrls.length > 0 && (
                    <div
                        className="mt-4 grid gap-2"
                        style={{ gridTemplateColumns: `repeat(${Math.min(post.imageUrls.length, 3)}, minmax(0, 1fr))` }}
                    >
                        {post.imageUrls.map((url) => (
                            <Image key={url} src={url} alt="" className="aspect-square rounded-md object-cover" />
                        ))}
                    </div>
                )}
            </div>
        </article>
    );
}
