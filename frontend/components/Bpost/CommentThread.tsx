'use client';

import { Avatar, Button, Input } from 'antd';
import { useEffect, useState } from 'react';
import { CommentDto, PageResponse } from '@/interface/BPost';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { fetchApi } from '@/utils/api';
import { formatRelative } from '@/utils/time';

export function CommentThread({ postId }: { postId: number }) {
    const [comments, setComments] = useState<CommentDto[]>([]);
    const [content, setContent] = useState('');
    const [loading, setLoading] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        let active = true;
        setLoading(true);
        fetchApi<PageResponse<CommentDto>>(API_SANDBOX.B_POST_COMMENT_LIST, { limit: 30 }, { postId })
            .then((res) => {
                if (active) setComments([...res.items].reverse());
            })
            .catch(console.error)
            .finally(() => active && setLoading(false));
        return () => {
            active = false;
        };
    }, [postId]);

    const handleSubmit = async () => {
        const text = content.trim();
        if (!text || submitting) return;
        setSubmitting(true);
        try {
            const created = await fetchApi<CommentDto>(API_SANDBOX.B_POST_COMMENT_ADD, { content: text }, { postId });
            setComments((prev) => [...prev, created]);
            setContent('');
        } catch (e) {
            console.error(e);
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="mt-3 space-y-3">
            <div className="space-y-3">
                {comments.map((c) => (
                    <div key={c.id} className="flex gap-2">
                        <Avatar src={c.author?.avatarUrl ?? undefined} size={32}>
                            {c.author?.displayName?.[0]}
                        </Avatar>
                        <div className="flex-1 rounded-2xl bg-muted px-3 py-2">
                            <div className="flex items-baseline justify-between gap-2">
                                <span className="text-sm font-semibold">{c.author?.displayName}</span>
                                <span className="text-xs text-text-secondary">{formatRelative(c.createdAt)}</span>
                            </div>
                            <p className="m-0 whitespace-pre-wrap text-sm">{c.content}</p>
                            {c.editedAt && <span className="text-xs italic text-text-secondary">(edited)</span>}
                        </div>
                    </div>
                ))}
                {!loading && comments.length === 0 && (
                    <p className="m-0 text-xs text-text-secondary">No comments yet — be the first.</p>
                )}
            </div>
            <div className="flex gap-2">
                <Input
                    value={content}
                    onChange={(e) => setContent(e.target.value)}
                    onPressEnter={handleSubmit}
                    placeholder="Write a comment..."
                />
                <Button type="primary" loading={submitting} onClick={handleSubmit}>
                    Send
                </Button>
            </div>
        </div>
    );
}
