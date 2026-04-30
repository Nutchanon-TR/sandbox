'use client';

import { Avatar, Button, Image, Popconfirm, Space, Tag, Typography } from 'antd';
import { CommentOutlined, DeleteOutlined, HeartFilled, HeartOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { PostDto } from '@/interface/BPost';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { fetchApi } from '@/utils/api';
import { useSessionStore } from '@/stores/sessionStore';
import { formatDateTime, formatRelative } from '@/utils/time';
import { CommentThread } from './CommentThread';

const { Text } = Typography;

export function PostCard({ post, onChange, onDelete }: {
    post: PostDto;
    onChange: (next: PostDto) => void;
    onDelete: (id: number) => void;
}) {
    const me = useSessionStore((s) => s.internalUserId);
    const [showComments, setShowComments] = useState(false);
    const [working, setWorking] = useState(false);

    const isMine = me != null && post.author?.id === me;

    const toggleLike = async () => {
        if (working) return;
        setWorking(true);
        const next = !post.likedByMe;
        // optimistic
        onChange({ ...post, likedByMe: next, likeCount: post.likeCount + (next ? 1 : -1) });
        try {
            await fetchApi(next ? API_SANDBOX.B_POST_POST_LIKE : API_SANDBOX.B_POST_POST_UNLIKE, {}, { postId: post.id });
        } catch (e) {
            console.error(e);
            // revert
            onChange(post);
        } finally {
            setWorking(false);
        }
    };

    const handleDelete = async () => {
        try {
            await fetchApi(API_SANDBOX.B_POST_POST_DELETE, {}, { postId: post.id });
            onDelete(post.id);
        } catch (e) {
            console.error(e);
        }
    };

    return (
        <div className="rounded-3xl border bg-surface p-5 shadow-sm">
            <div className="flex items-start justify-between">
                <Space>
                    <Avatar src={post.author?.avatarUrl ?? undefined} size={42}>
                        {post.author?.displayName?.[0]}
                    </Avatar>
                    <div>
                        <Text strong>{post.author?.displayName}</Text>
                        <div className="text-xs text-text-secondary" title={formatDateTime(post.createdAt)}>
                            {formatRelative(post.createdAt)}
                            {post.editedAt && <span className="ml-2 italic">(edited)</span>}
                            <Tag bordered={false} className="ml-2">{post.visibility}</Tag>
                        </div>
                    </div>
                </Space>
                {isMine && (
                    <Popconfirm title="Delete this post?" onConfirm={handleDelete} okText="Delete" cancelText="Cancel">
                        <Button type="text" icon={<DeleteOutlined />} />
                    </Popconfirm>
                )}
            </div>

            {post.content && <p className="mt-3 mb-0 whitespace-pre-wrap">{post.content}</p>}

            {post.imageUrls?.length > 0 && (
                <div className="mt-3 grid gap-2" style={{ gridTemplateColumns: `repeat(${Math.min(post.imageUrls.length, 3)}, minmax(0, 1fr))` }}>
                    {post.imageUrls.map((url) => (
                        <Image key={url} src={url} alt="post image" className="rounded-2xl" />
                    ))}
                </div>
            )}

            <div className="mt-3 flex items-center gap-2 border-t pt-3">
                <Button
                    type="text"
                    icon={post.likedByMe ? <HeartFilled className="text-red-500" /> : <HeartOutlined />}
                    onClick={toggleLike}
                    disabled={working}
                >
                    {post.likeCount}
                </Button>
                <Button type="text" icon={<CommentOutlined />} onClick={() => setShowComments((v) => !v)}>
                    {post.commentCount}
                </Button>
            </div>

            {showComments && <CommentThread postId={post.id} />}
        </div>
    );
}
