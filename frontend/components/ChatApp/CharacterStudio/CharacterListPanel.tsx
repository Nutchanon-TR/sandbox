'use client';

import { Avatar, Button, Empty, List, Spin, Tag, Tooltip, Typography } from 'antd';
import { MessageOutlined, PlusOutlined, RobotOutlined } from '@ant-design/icons';
import type { Character } from '@/interface/ChatApp';
import { formatStatus } from './characterStudioUtils';

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
    return (
        <aside className="flex max-h-[360px] w-full shrink-0 flex-col overflow-hidden rounded-2xl border border-border-main bg-surface shadow-sm xl:max-h-none xl:w-[340px] xl:min-w-[300px]">
            <div className="flex items-center justify-between px-5 py-4">
                <Typography.Title level={5} className="!m-0 !text-foreground">
                    Characters
                </Typography.Title>
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

            <div className="min-h-0 flex-1 overflow-y-auto p-3">
                {isLoading ? (
                    <div className="flex h-full items-center justify-center">
                        <Spin />
                    </div>
                ) : characters.length === 0 ? (
                    <div className="flex h-full items-center justify-center px-4">
                        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No characters" />
                    </div>
                ) : (
                    <List
                        dataSource={characters}
                        renderItem={(character) => {
                            const isSelected = selectedId === character.id;
                            return (
                                <List.Item
                                    className={`mb-2 cursor-pointer rounded-xl px-3 py-3 transition last:mb-0 ${isSelected ? 'bg-blue-50 shadow-sm dark:bg-blue-500/10' : 'hover:bg-muted'}`}
                                    onClick={() => onSelect(character.id)}
                                    actions={[
                                        <Tooltip title="Start chat" key="start-chat">
                                            <Button
                                                aria-label={`Start chat with ${character.aiName}`}
                                                type={isSelected ? 'primary' : 'text'}
                                                shape="circle"
                                                size="small"
                                                icon={<MessageOutlined />}
                                                loading={isSelected && isStartingChat}
                                                onClick={(event) => {
                                                    event.stopPropagation();
                                                    onStartChat(character);
                                                }}
                                            />
                                        </Tooltip>,
                                    ]}
                                >
                                    <List.Item.Meta
                                        avatar={<Avatar size={40} src={character.avatarUrl || undefined} icon={<RobotOutlined />} />}
                                        title={<span className="block truncate pr-2 text-sm font-medium text-foreground">{character.aiName}</span>}
                                        description={<Tag className="!m-0 rounded-full">{formatStatus(character.fineTuneStatus)}</Tag>}
                                    />
                                </List.Item>
                            );
                        }}
                    />
                )}
            </div>
        </aside>
    );
}
