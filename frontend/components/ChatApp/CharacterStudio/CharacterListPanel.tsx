'use client';

import { Avatar, Button, Empty, Skeleton, Tag, Tooltip, Typography } from 'antd';
import { MessageOutlined, PlusOutlined, RobotOutlined } from '@ant-design/icons';
import type { Character } from '@/interface/ChatApp';
import { formatStatus } from './characterStudioUtils';
import { STUDIO_BORDER } from './styles';

interface CharacterListPanelProps {
    characters: Character[];
    selectedId: number | null;
    isLoading: boolean;
    isStartingChat: boolean;
    onNew: () => void;
    onSelect: (characterId: number) => void;
    onStartChat: (character: Character) => void;
}

export function CharacterListPanel({
    characters,
    selectedId,
    isLoading,
    isStartingChat,
    onNew,
    onSelect,
    onStartChat,
}: CharacterListPanelProps) {
    const countLabel = isLoading
        ? 'Loading...'
        : `${characters.length} character${characters.length === 1 ? '' : 's'}`;

    return (
        <aside
            className={`flex max-h-[min(42vh,360px)] w-full shrink-0 flex-col overflow-hidden rounded-2xl border bg-surface shadow-sm xl:max-h-none xl:w-[320px] xl:min-w-[280px] 2xl:w-[340px] ${STUDIO_BORDER.main}`}
        >
            <div className={`flex shrink-0 items-center justify-between gap-3 border-b px-4 py-4 md:px-5 ${STUDIO_BORDER.main}`}>
                <div className="min-w-0">
                    <Typography.Title level={5} className="!mb-0.5 !text-foreground">
                        Characters
                    </Typography.Title>
                    <Typography.Text className="text-xs !text-text-secondary">{countLabel}</Typography.Text>
                </div>
                <Tooltip title="New character">
                    <Button
                        aria-label="New character"
                        icon={<PlusOutlined />}
                        shape="circle"
                        type="primary"
                        onClick={onNew}
                    />
                </Tooltip>
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto p-3 md:p-4">
                {isLoading ? (
                    <div className="space-y-2">
                        {Array.from({ length: 5 }).map((_, index) => (
                            <div
                                key={index}
                                className={`rounded-2xl border bg-background px-4 py-3 ${STUDIO_BORDER.main}`}
                            >
                                <Skeleton active title={{ width: '65%' }} paragraph={{ rows: 1, width: '45%' }} />
                            </div>
                        ))}
                    </div>
                ) : characters.length === 0 ? (
                    <div className="flex min-h-[12rem] items-center justify-center px-2 py-8">
                        <Empty
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            description="No characters yet"
                        >
                            <Button type="primary" icon={<PlusOutlined />} onClick={onNew}>
                                Create one
                            </Button>
                        </Empty>
                    </div>
                ) : (
                    <div className="space-y-2">
                        {characters.map((character) => {
                            const isSelected = selectedId === character.id;
                            return (
                                <div
                                    key={character.id}
                                    className={`flex items-center gap-2 rounded-2xl border px-2 py-2 transition-all ${isSelected
                                        ? 'border-blue-200 bg-blue-50 shadow-sm dark:border-blue-500/50 dark:bg-blue-500/10'
                                        : 'border-transparent bg-background hover:border-slate-300 hover:bg-muted dark:hover:border-border-main'
                                    }`}
                                >
                                    <button
                                        type="button"
                                        aria-current={isSelected ? 'true' : undefined}
                                        className="flex min-w-0 flex-1 items-center gap-3 rounded-xl px-2 py-1.5 text-left outline-none focus-visible:ring-2 focus-visible:ring-blue-500/60"
                                        onClick={() => onSelect(character.id)}
                                    >
                                        <Avatar
                                            size={40}
                                            src={character.avatarUrl || undefined}
                                            icon={<RobotOutlined />}
                                            className={`shrink-0 border bg-muted ${STUDIO_BORDER.main}`}
                                        />
                                        <div className="min-w-0 flex-1">
                                            <span className="block truncate text-sm font-medium text-foreground">
                                                {character.aiName}
                                            </span>
                                            <Tag className="!mt-1.5 !mr-0 rounded-full text-xs">
                                                {formatStatus(character.fineTuneStatus)}
                                            </Tag>
                                        </div>
                                    </button>
                                    <Tooltip title="Start chat">
                                        <Button
                                            aria-label={`Start chat with ${character.aiName}`}
                                            type={isSelected ? 'primary' : 'text'}
                                            shape="circle"
                                            icon={<MessageOutlined />}
                                            loading={isSelected && isStartingChat}
                                            onClick={() => onStartChat(character)}
                                        />
                                    </Tooltip>
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>
        </aside>
    );
}
