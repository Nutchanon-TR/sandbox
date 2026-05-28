'use client';

import { Button, Form, Input, InputNumber, Popconfirm, Select, Switch, Typography } from 'antd';
import { DeleteOutlined, SaveOutlined } from '@ant-design/icons';
import type { FormInstance } from 'antd';
import type { ReactNode } from 'react';
import type { Character } from '@/interface/ChatApp';
import type { CharacterFormValues } from './types';
import { buildCompiledPromptPreview, formatVisibilityLabel } from './characterStudioUtils';
import { ImagePreviewField } from './ImagePreviewField';
import { STUDIO_BORDER } from './styles';

interface CharacterEditorProps {
    form: FormInstance<CharacterFormValues>;
    selectedCharacter: Character | null;
    isSaving: boolean;
    onSave: () => void;
    onDelete: () => void;
    onValuesChange?: () => void;
}

function Section({
    title,
    description,
    children,
}: {
    title: string;
    description?: string;
    children: ReactNode;
}) {
    return (
        <section className="grid gap-4 py-8 first:pt-6 last:pb-6 md:first:pt-8 md:last:pb-8">
            <header className="grid gap-1">
                <Typography.Title level={5} className="!m-0 !text-foreground">
                    {title}
                </Typography.Title>
                {description && (
                    <Typography.Text type="secondary" className="text-xs leading-relaxed">
                        {description}
                    </Typography.Text>
                )}
            </header>
            {children}
        </section>
    );
}

const JSON_FIELD_RULE = {
    validator: (_: unknown, value?: string) => {
        if (!value?.trim()) return Promise.resolve();
        try {
            JSON.parse(value);
            return Promise.resolve();
        } catch {
            return Promise.reject(new Error('Must be valid JSON'));
        }
    },
};

export function CharacterEditor({
    form,
    selectedCharacter,
    isSaving,
    onSave,
    onDelete,
    onValuesChange,
}: CharacterEditorProps) {
    const avatarUrl = Form.useWatch('avatarUrl', form);
    const appearanceReferenceUrl = Form.useWatch('appearanceReferenceUrl', form);
    const posterUrl = Form.useWatch('posterUrl', form);
    const visibility = Form.useWatch('visibility', form);
    const aiName = Form.useWatch('aiName', form);
    const role = Form.useWatch('role', form);
    const character = Form.useWatch('character', form);
    const personalityTraits = Form.useWatch('personalityTraits', form);
    const biography = Form.useWatch('biography', form);
    const speechStyle = Form.useWatch('speechStyle', form);
    const relationshipContext = Form.useWatch('relationshipContext', form);
    const memoryNotes = Form.useWatch('memoryNotes', form);
    const responseBoundaries = Form.useWatch('responseBoundaries', form);
    const systemContext = Form.useWatch('systemContext', form);
    const rule = Form.useWatch('rule', form);
    const styleExamples = Form.useWatch('styleExamples', form);

    const compiledPromptPreview = buildCompiledPromptPreview({
        aiName,
        role,
        character,
        personalityTraits,
        biography,
        speechStyle,
        relationshipContext,
        memoryNotes,
        responseBoundaries,
        systemContext,
        rule,
        styleExamples,
    });

    const editorSubtitle = selectedCharacter
        ? formatVisibilityLabel(visibility ?? selectedCharacter.visibility)
        : 'Unsaved draft';

    return (
        <main className={`min-w-0 flex-1 overflow-y-auto rounded-2xl border bg-surface shadow-sm ${STUDIO_BORDER.main}`}>
            <div
                className={`sticky top-0 z-10 flex items-center justify-between gap-4 border-b bg-surface/95 px-4 py-4 backdrop-blur supports-[backdrop-filter]:bg-surface/80 md:px-6 ${STUDIO_BORDER.main}`}
            >
                <div className="min-w-0">
                    <Typography.Title level={4} className="!mb-0.5 truncate !text-foreground">
                        {selectedCharacter ? selectedCharacter.aiName : 'New character'}
                    </Typography.Title>
                    <Typography.Text type="secondary" className="truncate text-xs">
                        {editorSubtitle}
                    </Typography.Text>
                </div>
                <Button
                    type="primary"
                    icon={<SaveOutlined />}
                    loading={isSaving}
                    className="shrink-0"
                    onClick={onSave}
                >
                    Save
                </Button>
            </div>

            <Form
                form={form}
                layout="vertical"
                onValuesChange={onValuesChange}
                className={`character-studio-form grid divide-y p-4 md:p-6 ${STUDIO_BORDER.main}`}
            >
                <Section title="Basic information" description="Name and who can discover this character.">
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

                <Section title="Images" description="Avatar appears in chat; appearance reference guides generated photos.">
                    <div className="grid gap-4 xl:grid-cols-2 xl:gap-6">
                        <ImagePreviewField
                            name="avatarUrl"
                            label="Avatar URL"
                            placeholder="https://example.com/avatar.png"
                            url={avatarUrl}
                            enableAvatarUpload
                        />
                        <ImagePreviewField
                            name="appearanceReferenceUrl"
                            objectPathName="appearanceReferenceObjectPath"
                            label="Appearance Reference"
                            placeholder="Upload a clear face reference"
                            url={appearanceReferenceUrl}
                            enableAvatarUpload
                            uploadFolder="ai-appearance-references"
                            uploadButtonLabel="Upload reference"
                            cropTitle="Crop appearance reference"
                            uploadOkText="Upload reference"
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

                <Section title="AI configuration" description="Role, personality, and behavior the model should follow.">
                    <div className="grid gap-4">
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
                        <Form.Item name="styleExamples" label="Style Examples" rules={[JSON_FIELD_RULE]} className="!mb-0">
                            <Input.TextArea rows={4} placeholder="[]" />
                        </Form.Item>
                    </div>
                </Section>

                <Section title="System Context" description="Detailed prompt context injected for this character before chat history.">
                    <div className="grid gap-4">
                        <Form.Item name="personalityTraits" label="Personality Traits" rules={[JSON_FIELD_RULE]} className="!mb-0">
                            <Input.TextArea
                                rows={4}
                                placeholder={'["calm", "playful", "direct"]'}
                            />
                        </Form.Item>
                        <Form.Item name="speechStyle" label="Speech Style" className="!mb-0">
                            <Input.TextArea rows={4} placeholder="Tone, wording, catchphrases, reply length, and language habits." />
                        </Form.Item>
                        <Form.Item name="relationshipContext" label="Relationship Context" className="!mb-0">
                            <Input.TextArea rows={4} placeholder="How this character knows or relates to the user." />
                        </Form.Item>
                        <Form.Item name="memoryNotes" label="Memory Notes" className="!mb-0">
                            <Input.TextArea rows={5} placeholder="Important stable facts this character should remember." />
                        </Form.Item>
                        <Form.Item name="responseBoundaries" label="Response Boundaries" className="!mb-0">
                            <Input.TextArea rows={4} placeholder="Topics, tone, or behaviors this character should avoid." />
                        </Form.Item>
                        <Form.Item name="systemContext" label="Advanced System Context" className="!mb-0">
                            <Input.TextArea rows={6} placeholder="Optional advanced instructions. This is added after structured fields." />
                        </Form.Item>
                        <div className={`rounded-xl border bg-surface-hover/40 p-4 ${STUDIO_BORDER.main}`}>
                            <Typography.Text strong className="text-sm">
                                Compiled Prompt Preview
                            </Typography.Text>
                            <Input.TextArea
                                className="!mt-3 font-mono text-xs"
                                value={compiledPromptPreview}
                                autoSize={{ minRows: 10, maxRows: 18 }}
                                readOnly
                            />
                        </div>
                    </div>
                </Section>

                <Section title="PersonaFeed">
                    <div className="grid gap-4">
                        <div className={`flex items-center justify-between gap-4 rounded-xl border bg-surface-hover/50 px-4 py-3 ${STUDIO_BORDER.main}`}>
                            <Typography.Text strong className="text-sm">
                                Enabled
                            </Typography.Text>
                            <Form.Item name="personaFeedEnabled" valuePropName="checked" className="!mb-0">
                                <Switch disabled={visibility !== 'public'} />
                            </Form.Item>
                        </div>

                        <div className="grid gap-4 lg:grid-cols-2">
                            <Form.Item
                                name="personaFeedMinIntervalHours"
                                label="Minimum interval"
                                rules={[{ required: true, message: 'Minimum interval is required' }]}
                                className="!mb-0"
                            >
                                <InputNumber min={1} max={720} addonAfter="hours" className="!w-full" />
                            </Form.Item>
                            <Form.Item
                                name="personaFeedMaxIntervalHours"
                                label="Maximum interval"
                                rules={[{ required: true, message: 'Maximum interval is required' }]}
                                className="!mb-0"
                            >
                                <InputNumber min={1} max={720} addonAfter="hours" className="!w-full" />
                            </Form.Item>
                        </div>

                        <div className="grid gap-4 lg:grid-cols-2">
                            <Form.Item name="personaFeedWindowStart" label="Posting window start" className="!mb-0">
                                <Input type="time" />
                            </Form.Item>
                            <Form.Item name="personaFeedWindowEnd" label="Posting window end" className="!mb-0">
                                <Input type="time" />
                            </Form.Item>
                        </div>

                        <Form.Item
                            name="personaFeedTimezone"
                            label="Timezone"
                            rules={[{ required: true, message: 'Timezone is required' }]}
                            className="!mb-0"
                        >
                            <Input placeholder="Asia/Bangkok" />
                        </Form.Item>
                    </div>
                </Section>

                <Section title="Image behavior" description="The model decides when an image is useful; the backend handles the hidden trigger marker.">
                    <div className="grid gap-4">
                        <div className={`flex items-center justify-between gap-4 rounded-xl border bg-surface-hover/50 px-4 py-3 ${STUDIO_BORDER.main}`}>
                            <div className="min-w-0">
                                <Typography.Text strong className="text-sm">
                                    Image enabled
                                </Typography.Text>
                                <Typography.Paragraph className="!mb-0 !mt-0.5 text-xs" type="secondary">
                                    Allow the character to attach generated images when the conversation naturally calls for one.
                                </Typography.Paragraph>
                            </div>
                            <Form.Item name="imageEnabled" valuePropName="checked" className="!mb-0">
                                <Switch />
                            </Form.Item>
                        </div>
                        <Form.Item name="imagePromptTemplate" label="Image Prompt Template" className="!mb-0">
                            <Input.TextArea
                                rows={4}
                                placeholder="Optional: guide the image style. Supports {user_message}, {assistant_reply}, {ai_name}, {character_biography}, and {random_activity}."
                            />
                        </Form.Item>
                    </div>
                </Section>

                <Section title="Danger zone">
                    <div className="flex flex-col gap-4 rounded-xl border border-red-200/80 bg-red-50/60 p-4 dark:border-red-900/50 dark:bg-red-950/25 sm:flex-row sm:items-center sm:justify-between">
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
