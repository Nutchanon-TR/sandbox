'use client';

import { useEffect, useRef, useState } from 'react';
import { Button, Form, Input, Modal, Slider, Typography, Upload } from 'antd';
import type { UploadProps } from 'antd';
import { PictureOutlined, UploadOutlined } from '@ant-design/icons';
import { createSupabaseBrowser } from '@/lib/supabase/client';
import type { CharacterFormValues } from './types';
import { STUDIO_BORDER } from './styles';

interface ImagePreviewFieldProps {
    name: keyof CharacterFormValues;
    label: string;
    placeholder: string;
    url?: string;
    aspectClassName?: string;
    enableAvatarUpload?: boolean;
    objectPathName?: keyof CharacterFormValues;
    uploadFolder?: string;
    uploadButtonLabel?: string;
    cropTitle?: string;
    uploadOkText?: string;
}

type CropState = {
    zoom: number;
    x: number;
    y: number;
};

const AVATAR_BUCKET = 'images';
const AVATAR_FOLDER = 'ai-avatars';
const AVATAR_OUTPUT_SIZE = 512;
const AVATAR_PREVIEW_SIZE = 280;
const MAX_AVATAR_SIZE_BYTES = 8 * 1024 * 1024;
const INITIAL_CROP: CropState = { zoom: 1, x: 0, y: 0 };

function getRandomImagePath(folder: string) {
    const randomId = typeof crypto !== 'undefined' && 'randomUUID' in crypto
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).slice(2)}`;

    return `${folder}/${randomId}.jpg`;
}

function getCropRect(image: HTMLImageElement, crop: CropState) {
    const width = image.naturalWidth || image.width;
    const height = image.naturalHeight || image.height;
    const size = Math.min(width, height) / crop.zoom;
    const maxX = Math.max(width - size, 0);
    const maxY = Math.max(height - size, 0);

    return {
        sx: maxX * ((crop.x + 50) / 100),
        sy: maxY * ((crop.y + 50) / 100),
        size,
    };
}

function drawCrop(image: HTMLImageElement, crop: CropState, canvas: HTMLCanvasElement, size: number) {
    const context = canvas.getContext('2d');
    if (!context) return;

    const rect = getCropRect(image, crop);
    canvas.width = size;
    canvas.height = size;
    context.clearRect(0, 0, size, size);
    context.drawImage(image, rect.sx, rect.sy, rect.size, rect.size, 0, 0, size, size);
}

function canvasToBlob(canvas: HTMLCanvasElement): Promise<Blob> {
    return new Promise((resolve, reject) => {
        canvas.toBlob((blob) => {
            if (!blob) {
                reject(new Error('Could not prepare avatar image'));
                return;
            }
            resolve(blob);
        }, 'image/jpeg', 0.92);
    });
}

export function ImagePreviewField({
    name,
    label,
    placeholder,
    url,
    aspectClassName = 'aspect-square',
    enableAvatarUpload = false,
    objectPathName,
    uploadFolder = AVATAR_FOLDER,
    uploadButtonLabel = 'Upload & crop',
    cropTitle = 'Crop avatar',
    uploadOkText = 'Upload avatar',
}: ImagePreviewFieldProps) {
    const form = Form.useFormInstance();
    const hasUrl = Boolean(url?.trim());
    const imageRef = useRef<HTMLImageElement | null>(null);
    const previewCanvasRef = useRef<HTMLCanvasElement | null>(null);
    const [cropImageUrl, setCropImageUrl] = useState<string | null>(null);
    const [crop, setCrop] = useState<CropState>(INITIAL_CROP);
    const [imageReady, setImageReady] = useState(false);
    const [isCropOpen, setIsCropOpen] = useState(false);
    const [isUploading, setIsUploading] = useState(false);
    const [uploadError, setUploadError] = useState<string | null>(null);

    useEffect(() => {
        if (!cropImageUrl) return;

        const image = new window.Image();
        image.onload = () => {
            imageRef.current = image;
            setImageReady(true);
        };
        image.onerror = () => {
            setUploadError('Could not read this image file.');
            setImageReady(false);
        };
        image.src = cropImageUrl;

        return () => {
            image.onload = null;
            image.onerror = null;
            imageRef.current = null;
            setImageReady(false);
            URL.revokeObjectURL(cropImageUrl);
        };
    }, [cropImageUrl]);

    useEffect(() => {
        if (!imageReady || !imageRef.current || !previewCanvasRef.current) return;
        drawCrop(imageRef.current, crop, previewCanvasRef.current, AVATAR_PREVIEW_SIZE);
    }, [crop, imageReady]);

    const handleBeforeUpload: UploadProps['beforeUpload'] = (file) => {
        setUploadError(null);

        if (!file.type.startsWith('image/')) {
            setUploadError('Please choose an image file.');
            return Upload.LIST_IGNORE;
        }

        if (file.size > MAX_AVATAR_SIZE_BYTES) {
            setUploadError('Image must be 8 MB or smaller.');
            return Upload.LIST_IGNORE;
        }

        setCrop(INITIAL_CROP);
        setCropImageUrl(URL.createObjectURL(file));
        setIsCropOpen(true);
        return Upload.LIST_IGNORE;
    };

    const closeCropModal = () => {
        setIsCropOpen(false);
        setCropImageUrl(null);
        setCrop(INITIAL_CROP);
    };

    const handleUploadCroppedAvatar = async () => {
        if (!imageRef.current) return;

        setIsUploading(true);
        setUploadError(null);

        try {
            const canvas = document.createElement('canvas');
            drawCrop(imageRef.current, crop, canvas, AVATAR_OUTPUT_SIZE);
            const blob = await canvasToBlob(canvas);
            const filePath = getRandomImagePath(uploadFolder);
            const supabase = createSupabaseBrowser();
            const { error } = await supabase.storage
                .from(AVATAR_BUCKET)
                .upload(filePath, blob, {
                    cacheControl: '3600',
                    contentType: 'image/jpeg',
                    upsert: false,
                });

            if (error) throw new Error(error.message);

            const { data } = supabase.storage.from(AVATAR_BUCKET).getPublicUrl(filePath);
            form.setFieldValue(name, data.publicUrl);
            if (objectPathName) {
                form.setFieldValue(objectPathName, filePath);
            }
            closeCropModal();
        } catch (error) {
            setUploadError(error instanceof Error ? error.message : 'Failed to upload image.');
        } finally {
            setIsUploading(false);
        }
    };

    return (
        <div
            className={`grid gap-4 rounded-xl border bg-surface-hover/40 p-4 sm:grid-cols-[minmax(7rem,9rem)_minmax(0,1fr)] sm:items-start ${STUDIO_BORDER.main}`}
        >
            <div
                className={`relative flex ${aspectClassName} min-h-[7.5rem] w-full max-w-[9rem] items-center justify-center overflow-hidden rounded-xl border bg-muted shadow-sm transition-shadow sm:max-w-none sm:justify-self-start ${hasUrl ? STUDIO_BORDER.main : 'border-dashed'}`}
                style={hasUrl ? {
                    backgroundImage: `url("${url}")`,
                    backgroundPosition: 'center',
                    backgroundSize: 'cover',
                } : undefined}
            >
                {!hasUrl && <PictureOutlined className="text-2xl text-text-secondary" aria-hidden />}
            </div>
            <div className="grid min-w-0 gap-3 sm:col-span-1">
                <Form.Item name={name} label={label} className="!mb-0 min-w-0">
                    <Input
                        allowClear
                        placeholder={placeholder}
                        onChange={() => {
                            if (objectPathName) form.setFieldValue(objectPathName, '');
                        }}
                    />
                </Form.Item>
                {objectPathName && (
                    <Form.Item name={objectPathName} hidden>
                        <Input />
                    </Form.Item>
                )}
                {enableAvatarUpload && (
                    <div className="grid gap-2">
                        <Upload
                            accept="image/*"
                            beforeUpload={handleBeforeUpload}
                            maxCount={1}
                            showUploadList={false}
                        >
                            <Button icon={<UploadOutlined />}>{uploadButtonLabel}</Button>
                        </Upload>
                        <Typography.Text type="secondary" className="text-xs leading-relaxed">
                            JPEG up to 8 MB - stored in Supabase with a random file name.
                        </Typography.Text>
                    </div>
                )}
                {uploadError && (
                    <Typography.Text type="danger" className="text-xs" role="alert">
                        {uploadError}
                    </Typography.Text>
                )}
                {hasUrl && (
                    <Typography.Text type="secondary" className="text-xs" ellipsis={{ tooltip: url }}>
                        {url}
                    </Typography.Text>
                )}
            </div>
            {enableAvatarUpload && (
                <Modal
                    title={cropTitle}
                    open={isCropOpen}
                    okText={uploadOkText}
                    confirmLoading={isUploading}
                    onCancel={closeCropModal}
                    onOk={handleUploadCroppedAvatar}
                    destroyOnHidden
                >
                    <div className="grid gap-5">
                        <div className="flex justify-center">
                            <canvas
                                ref={previewCanvasRef}
                                className="max-w-full rounded-xl bg-muted shadow-sm"
                                height={AVATAR_PREVIEW_SIZE}
                                width={AVATAR_PREVIEW_SIZE}
                            />
                        </div>
                        <div className="grid gap-3">
                            <div>
                                <Typography.Text type="secondary">Zoom</Typography.Text>
                                <Slider
                                    max={3}
                                    min={1}
                                    step={0.05}
                                    value={crop.zoom}
                                    onChange={(value) => setCrop((current) => ({ ...current, zoom: value }))}
                                />
                            </div>
                            <div>
                                <Typography.Text type="secondary">Horizontal</Typography.Text>
                                <Slider
                                    max={50}
                                    min={-50}
                                    value={crop.x}
                                    onChange={(value) => setCrop((current) => ({ ...current, x: value }))}
                                />
                            </div>
                            <div>
                                <Typography.Text type="secondary">Vertical</Typography.Text>
                                <Slider
                                    max={50}
                                    min={-50}
                                    value={crop.y}
                                    onChange={(value) => setCrop((current) => ({ ...current, y: value }))}
                                />
                            </div>
                        </div>
                    </div>
                </Modal>
            )}
        </div>
    );
}
