'use client';

import { Form, Input, Typography } from 'antd';
import { PictureOutlined } from '@ant-design/icons';
import type { CharacterFormValues } from './types';

interface ImagePreviewFieldProps {
    name: keyof CharacterFormValues;
    label: string;
    placeholder: string;
    url?: string;
    aspectClassName?: string;
}

export function ImagePreviewField({
    name,
    label,
    placeholder,
    url,
    aspectClassName = 'aspect-square',
}: ImagePreviewFieldProps) {
    const hasUrl = Boolean(url?.trim());

    return (
        <div className="grid gap-4 rounded-xl bg-surface-hover/70 p-4 md:grid-cols-[144px_minmax(0,1fr)]">
            <div
                className={`flex ${aspectClassName} min-h-[128px] items-center justify-center overflow-hidden rounded-xl bg-muted`}
                style={hasUrl ? {
                    backgroundImage: `url("${url}")`,
                    backgroundPosition: 'center',
                    backgroundSize: 'cover',
                } : undefined}
            >
                {!hasUrl && <PictureOutlined className="text-2xl text-text-secondary" />}
            </div>
            <Form.Item name={name} label={label} className="!mb-0 min-w-0">
                <Input allowClear placeholder={placeholder} />
            </Form.Item>
            {hasUrl && (
                <Typography.Text className="md:col-start-2" type="secondary" ellipsis>
                    {url}
                </Typography.Text>
            )}
        </div>
    );
}
