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
        <aside className="flex max-h-[320px] w-full shrink-0 flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950 xl:max-h-none xl:w-[320px] xl:min-w-[280px]">
            <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3 dark:border-slate-800">
                <Typography.Title level={5} className="!m-0">
                    Characters
                </Typography.Title>
                <Tooltip title="New character">
                    <Button aria-label="New character" icon={<PlusOutlined />} onClick={onNew} />
                </Tooltip>
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto">
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
                                    className={`cursor-pointer px-4 py-3 transition ${isSelected ? 'bg-blue-50 dark:bg-blue-950/40' : 'hover:bg-slate-50 dark:hover:bg-slate-900/60'}`}
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
                                        avatar={<Avatar src={character.avatarUrl || undefined} icon={<RobotOutlined />} />}
                                        title={<span className="block truncate pr-2">{character.aiName}</span>}
                                        description={<Tag className="!m-0">{formatStatus(character.fineTuneStatus)}</Tag>}
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
