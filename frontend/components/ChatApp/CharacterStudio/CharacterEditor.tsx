'use client';

import { Button, Form, Input, Popconfirm, Select, Switch, Typography } from 'antd';
import { DeleteOutlined, SaveOutlined } from '@ant-design/icons';
import type { FormInstance } from 'antd';
import type { ReactNode } from 'react';
import type { Character } from '@/interface/ChatApp';
import type { CharacterFormValues } from './types';
import { ImagePreviewField } from './ImagePreviewField';

interface CharacterEditorProps {
    form: FormInstance<CharacterFormValues>;
    selectedCharacter: Character | null;
    isSaving: boolean;
    onSave: () => void;
    onDelete: () => void;
}

function Section({
    title,
    children,
}: {
    title: string;
    children: ReactNode;
}) {
    return (
        <section className="grid gap-5 pb-8 last:pb-0">
            <Typography.Title level={5} className="!m-0 !text-foreground">
                {title}
            </Typography.Title>
            {children}
        </section>
    );
}

export function CharacterEditor({
    form,
    selectedCharacter,
    isSaving,
    onSave,
    onDelete,
}: CharacterEditorProps) {
    const avatarUrl = Form.useWatch('avatarUrl', form);
    const posterUrl = Form.useWatch('posterUrl', form);

    return (
        <main className="min-w-0 flex-1 overflow-y-auto rounded-2xl border border-border-main bg-surface shadow-sm">
            <div className="sticky top-0 z-10 flex items-center justify-between gap-4 bg-surface/95 px-5 py-4 backdrop-blur md:px-6">
                <div className="min-w-0">
                    <Typography.Title level={4} className="!m-0 truncate !text-foreground">
                        {selectedCharacter ? selectedCharacter.aiName : 'New Character'}
                    </Typography.Title>
                </div>
                <Button
                    type="primary"
                    icon={<SaveOutlined />}
                    loading={isSaving}
                    onClick={onSave}
                >
                    Save
                </Button>
            </div>

            <Form
                form={form}
                layout="vertical"
                className="character-studio-form grid gap-9 p-5 md:p-6"
            >
                <Section title="Basic Information">
                    <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_240px]">
                        <Form.Item
                            name="aiName"
                            label="Name"
                            rules={[{ required: true, message: 'Name is required' }]}
                            className="!mb-0"
                        >
                            <Input maxLength={100} placeholder="AI name" />
                        </Form.Item>
                        <Form.Item name="visibility" label="Visibility" className="!mb-0">
                            <Select
                                options={[
                                    { value: 'private', label: 'Private' },
                                    { value: 'unlisted', label: 'Unlisted' },
                                    { value: 'public', label: 'Public' },
                                ]}
                            />
                        </Form.Item>
                    </div>
                </Section>

                <Section title="Images">
                    <div className="grid gap-5 xl:grid-cols-2">
                        <ImagePreviewField
                            name="avatarUrl"
                            label="Avatar URL"
                            placeholder="https://example.com/avatar.png"
                            url={avatarUrl}
                        />
                        <ImagePreviewField
                            name="posterUrl"
                            label="Poster URL"
                            placeholder="https://example.com/poster.png"
                            url={posterUrl}
                            aspectClassName="aspect-[4/3]"
                        />
                    </div>
                </Section>

                <Section title="AI Configuration">
                    <div className="grid gap-5">
                        <Form.Item name="role" label="Role" className="!mb-0">
                            <Input.TextArea rows={3} placeholder="Assistant role" />
                        </Form.Item>
                        <Form.Item name="character" label="Character" className="!mb-0">
                            <Input.TextArea rows={4} placeholder="Personality and tone" />
                        </Form.Item>
                        <Form.Item name="biography" label="Biography" className="!mb-0">
                            <Input.TextArea rows={5} placeholder="Background and story" />
                        </Form.Item>
                        <Form.Item name="rule" label="Rules" className="!mb-0">
                            <Input.TextArea rows={5} placeholder="Behavior rules" />
                        </Form.Item>
                        <Form.Item name="styleExamples" label="Style Examples" className="!mb-0">
                            <Input.TextArea rows={4} placeholder="[]" />
                        </Form.Item>
                    </div>
                </Section>

                <Section title="Image Trigger">
                    <div className="grid gap-5">
                        <Form.Item name="imageEnabled" label="Image Enabled" valuePropName="checked" className="!mb-0">
                            <Switch />
                        </Form.Item>
                        <div className="grid gap-5 lg:grid-cols-2">
                            <Form.Item name="photoKeywords" label="Photo Keywords" className="!mb-0">
                                <Input.TextArea rows={3} />
                            </Form.Item>
                            <Form.Item name="activityKeywords" label="Activity Keywords" className="!mb-0">
                                <Input.TextArea rows={3} />
                            </Form.Item>
                        </div>
                        <Form.Item name="imagePromptTemplate" label="Image Prompt Template" className="!mb-0">
                            <Input.TextArea rows={4} placeholder="Optional template" />
                        </Form.Item>
                    </div>
                </Section>

                <Section title="Danger Zone">
                    <div className="flex flex-col gap-4 rounded-xl bg-red-50/70 p-4 dark:bg-red-950/20 sm:flex-row sm:items-center sm:justify-between">
                        <div className="min-w-0">
                            <Typography.Text strong>Delete character</Typography.Text>
                            <Typography.Paragraph className="!mb-0 !mt-1 text-sm" type="secondary">
                                This removes the character from your studio list.
                            </Typography.Paragraph>
                        </div>
                        <Popconfirm
                            title="Delete character?"
                            okText="Delete"
                            okButtonProps={{ danger: true }}
                            disabled={!selectedCharacter}
                            onConfirm={onDelete}
                        >
                            <Button danger icon={<DeleteOutlined />} disabled={!selectedCharacter}>
                                Delete
                            </Button>
                        </Popconfirm>
                    </div>
                </Section>
            </Form>
        </main>
    );
}
