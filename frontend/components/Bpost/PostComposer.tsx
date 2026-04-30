'use client';

import { Button, Input, Upload, Space, Image } from 'antd';
import { PictureOutlined, SendOutlined } from '@ant-design/icons';
import { useState } from 'react';
import api from '@/config/axiosConfig';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { fetchApi } from '@/utils/api';
import { PostDto } from '@/interface/BPost';
import { useNotification } from '@/providers/NotificationProvider';

const { TextArea } = Input;

export function PostComposer({ onCreated }: { onCreated: (post: PostDto) => void }) {
    const [content, setContent] = useState('');
    const [imageUrls, setImageUrls] = useState<string[]>([]);
    const [submitting, setSubmitting] = useState(false);
    const [uploading, setUploading] = useState(false);
    const notification = useNotification();

    const handleUpload = async (file: File) => {
        setUploading(true);
        try {
            const form = new FormData();
            form.append('imageFile', file);
            const { data } = await api.post<{ url: string }>(API_SANDBOX.B_POST_POST_UPLOAD_IMAGE.path, form);
            setImageUrls((prev) => [...prev, data.url]);
        } catch (e) {
            console.error(e);
            notification.error({ message: 'Upload failed' });
        } finally {
            setUploading(false);
        }
        return false;
    };

    const handleSubmit = async () => {
        if (!content.trim() && imageUrls.length === 0) return;
        setSubmitting(true);
        try {
            const post = await fetchApi<PostDto>(API_SANDBOX.B_POST_POST_CREATE, {
                content: content.trim(),
                imageUrls,
                visibility: 'FRIENDS',
            });
            onCreated(post);
            setContent('');
            setImageUrls([]);
        } catch (e) {
            console.error(e);
            notification.error({ message: 'Failed to publish post' });
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="rounded-3xl border bg-surface p-5 shadow-sm">
            <TextArea
                rows={3}
                value={content}
                onChange={(e) => setContent(e.target.value)}
                placeholder="What's on your mind?"
                className="!resize-none !rounded-2xl"
            />
            {imageUrls.length > 0 && (
                <div className="mt-3 grid grid-cols-3 gap-2">
                    {imageUrls.map((url) => (
                        <Image key={url} src={url} alt="upload" className="rounded-xl" />
                    ))}
                </div>
            )}
            <div className="mt-3 flex items-center justify-between">
                <Upload beforeUpload={(file) => handleUpload(file as File)} showUploadList={false} accept="image/*">
                    <Button icon={<PictureOutlined />} loading={uploading} type="text">
                        Add photo
                    </Button>
                </Upload>
                <Space>
                    <Button
                        type="primary"
                        icon={<SendOutlined />}
                        loading={submitting}
                        onClick={handleSubmit}
                        disabled={!content.trim() && imageUrls.length === 0}
                    >
                        Post
                    </Button>
                </Space>
            </div>
        </div>
    );
}
