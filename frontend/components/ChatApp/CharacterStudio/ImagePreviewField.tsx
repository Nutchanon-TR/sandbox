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
        <div className="grid gap-3 rounded-lg border border-slate-200 p-3 dark:border-slate-800 md:grid-cols-[132px_minmax(0,1fr)]">
            <div
                className={`flex ${aspectClassName} min-h-[112px] items-center justify-center overflow-hidden rounded-lg border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900`}
                style={hasUrl ? {
                    backgroundImage: `url("${url}")`,
                    backgroundPosition: 'center',
                    backgroundSize: 'cover',
                } : undefined}
            >
                {!hasUrl && <PictureOutlined className="text-2xl text-slate-400 dark:text-slate-600" />}
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
