'use client';

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Form, Spin } from 'antd';
import { useRouter } from 'next/navigation';
import { TITLE } from '@/constants/Title';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import {
    CharacterEditor,
    CharacterListPanel,
    DEFAULT_FORM_VALUES,
    buildPayload,
    characterToFormValues,
    getErrorMessage,
    type CharacterFormValues,
} from '@/components/ChatApp/CharacterStudio';
import type { Character, RoomCreateResponse, RoomSummary } from '@/interface/ChatApp';
import { useNotification } from '@/providers/NotificationProvider';
import { useSessionStore } from '@/stores/sessionStore';
import { fetchApi } from '@/utils/api';
import { useChangeTitle } from '@/utils/breadCrumbUtil';
import { useChangeSubSideBar } from '@/utils/subSideBarUtil';

export default function CharacterStudioPage() {
    const [form] = Form.useForm<CharacterFormValues>();
    const router = useRouter();
    const notification = useNotification();
    const status = useSessionStore((s) => s.status);
    const currentUserId = useSessionStore((s) => s.internalUserId);

    const [characters, setCharacters] = useState<Character[]>([]);
    const [selectedId, setSelectedId] = useState<number | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [isStartingChat, setIsStartingChat] = useState(false);

    const selectedCharacter = useMemo(
        () => characters.find((character) => character.id === selectedId) ?? null,
        [characters, selectedId],
    );

    useChangeTitle(TITLE.CHAT_APP, 'CHARACTER_STUDIO');
    useChangeSubSideBar(null);

    const loadCharacters = useCallback(async () => {
        if (currentUserId === null) return;

        setIsLoading(true);
        try {
            const response = await fetchApi<Character[]>(API_SANDBOX.CHAT_APP_CHARACTER_LIST);
            setCharacters(response);
            if (response.length > 0) {
                setSelectedId((current) => current ?? response[0].id);
            } else {
                setSelectedId(null);
                form.setFieldsValue(DEFAULT_FORM_VALUES);
            }
        } catch (error) {
            notification.error({
                message: 'Error',
                description: getErrorMessage(error, 'Failed to load characters'),
            });
        } finally {
            setIsLoading(false);
        }
    }, [currentUserId, form, notification]);

    useEffect(() => {
        void loadCharacters();
    }, [loadCharacters]);

    useEffect(() => {
        form.setFieldsValue(selectedCharacter ? characterToFormValues(selectedCharacter) : DEFAULT_FORM_VALUES);
    }, [form, selectedCharacter]);

    const handleNew = () => {
        setSelectedId(null);
        form.resetFields();
        form.setFieldsValue(DEFAULT_FORM_VALUES);
    };

    const createRoomForCharacter = useCallback(async (character: Character) => {
        const rooms = await fetchApi<RoomSummary[]>(API_SANDBOX.CHAT_APP_ROOM_LIST);
        const existingRoom = rooms.find((room) => room.aiContextId === character.id);
        if (existingRoom) {
            return existingRoom;
        }

        return fetchApi<RoomCreateResponse>(API_SANDBOX.CHAT_APP_ROOM_CREATE, {
            aiContextId: character.id,
            name: character.aiName,
            isGroup: false,
        });
    }, []);

    const handleSave = async () => {
        try {
            const values = await form.validateFields();
            const payload = buildPayload(values);

            setIsSaving(true);
            if (selectedCharacter) {
                const updated = await fetchApi<Character>(
                    API_SANDBOX.CHAT_APP_CHARACTER_UPDATE,
                    payload,
                    { characterId: selectedCharacter.id },
                );
                setCharacters((prev) => prev.map((item) => (item.id === updated.id ? updated : item)));
                notification.success({ message: 'Saved' });
            } else {
                const created = await fetchApi<Character>(API_SANDBOX.CHAT_APP_CHARACTER_CREATE, payload);
                setCharacters((prev) => [created, ...prev]);
                setSelectedId(created.id);
                try {
                    await createRoomForCharacter(created);
                    notification.success({
                        message: 'Created',
                        description: 'Character added to Messages.',
                    });
                } catch (roomError) {
                    notification.warning({
                        message: 'Created',
                        description: getErrorMessage(roomError, 'Character created, but could not be added to Messages.'),
                    });
                }
            }
        } catch (error) {
            if (typeof error === 'object' && error !== null && 'errorFields' in error) return;
            notification.error({
                message: 'Error',
                description: getErrorMessage(error, 'Failed to save character'),
            });
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async () => {
        if (!selectedCharacter) return;

        try {
            await fetchApi<void>(
                API_SANDBOX.CHAT_APP_CHARACTER_DELETE,
                {},
                { characterId: selectedCharacter.id },
            );
            setCharacters((prev) => prev.filter((item) => item.id !== selectedCharacter.id));
            setSelectedId(null);
            form.setFieldsValue(DEFAULT_FORM_VALUES);
            notification.success({ message: 'Deleted' });
        } catch (error) {
            notification.error({
                message: 'Error',
                description: getErrorMessage(error, 'Failed to delete character'),
            });
        }
    };

    const handleStartChat = async (character: Character) => {
        setSelectedId(character.id);
        setIsStartingChat(true);
        try {
            const room = await createRoomForCharacter(character);
            router.push(`/chat-app/message?roomId=${room.id}`);
        } catch (error) {
            notification.error({
                message: 'Error',
                description: getErrorMessage(error, 'Failed to start chat'),
            });
        } finally {
            setIsStartingChat(false);
        }
    };

    if (status === 'loading') {
        return (
            <div className="flex h-full items-center justify-center">
                <Spin size="large" />
            </div>
        );
    }

    if (status === 'unauthenticated') {
        return (
            <div className="flex h-full items-center justify-center text-slate-500">
                <p>Please log in to use Character Studio.</p>
            </div>
        );
    }

    return (
        <div className="flex h-full min-h-0 min-w-0 flex-1 flex-col gap-4 overflow-hidden p-3 md:gap-5 md:p-4 xl:flex-row xl:gap-6">
            <CharacterListPanel
                characters={characters}
                selectedId={selectedId}
                isLoading={isLoading}
                isStartingChat={isStartingChat}
                onNew={handleNew}
                onSelect={setSelectedId}
                onStartChat={handleStartChat}
            />
            <CharacterEditor
                form={form}
                selectedCharacter={selectedCharacter}
                isSaving={isSaving}
                onSave={handleSave}
                onDelete={handleDelete}
            />
        </div>
    );
}
