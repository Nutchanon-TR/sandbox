'use client';

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
    const [devPublishingCharacterId, setDevPublishingCharacterId] = useState<number | null>(null);
    const [, setIsDirty] = useState(false);
    const [isDraftActive, setIsDraftActive] = useState(false);
    const isDirtyRef = useRef(false);

    const selectedCharacter = useMemo(
        () => characters.find((character) => character.id === selectedId) ?? null,
        [characters, selectedId],
    );

    useChangeTitle(TITLE.CHAT_APP, 'CHARACTER_STUDIO');
    useChangeSubSideBar(null);

    const setDirtyState = useCallback((nextValue: boolean) => {
        isDirtyRef.current = nextValue;
        setIsDirty(nextValue);
    }, []);

    const confirmDiscardChanges = useCallback(() => {
        if (!isDirtyRef.current) return true;
        const shouldLeave = window.confirm('You have unsaved changes. Leave this page?');
        if (shouldLeave) {
            setDirtyState(false);
        }
        return shouldLeave;
    }, [setDirtyState]);

    const loadCharacters = useCallback(async () => {
        if (currentUserId === null) return;

        setIsLoading(true);
        try {
            const response = await fetchApi<Character[]>(API_SANDBOX.CHAT_APP_CHARACTER_LIST);
            setCharacters(response);
            if (response.length > 0) {
                setSelectedId((current) => current ?? response[0].id);
                setIsDraftActive(false);
                setDirtyState(false);
            } else {
                setSelectedId(null);
                form.setFieldsValue(DEFAULT_FORM_VALUES);
                setIsDraftActive(false);
                setDirtyState(false);
            }
        } catch (error) {
            notification.error({
                message: 'Error',
                description: getErrorMessage(error, 'Failed to load characters'),
            });
        } finally {
            setIsLoading(false);
        }
    }, [currentUserId, form, notification, setDirtyState]);

    useEffect(() => {
        void loadCharacters();
    }, [loadCharacters]);

    useEffect(() => {
        if (selectedCharacter) {
            form.setFieldsValue(characterToFormValues(selectedCharacter));
            setIsDraftActive(false);
            setDirtyState(false);
            return;
        }

        if (!isDraftActive) {
            form.setFieldsValue(DEFAULT_FORM_VALUES);
            setDirtyState(false);
        }
    }, [form, isDraftActive, selectedCharacter, setDirtyState]);

    useEffect(() => {
        const handleBeforeUnload = (event: BeforeUnloadEvent) => {
            if (!isDirtyRef.current) return;
            event.preventDefault();
            event.returnValue = '';
        };

        const handleDocumentClick = (event: MouseEvent) => {
            if (!isDirtyRef.current || event.defaultPrevented || event.button !== 0) return;
            if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;

            const target = event.target as Element | null;
            const anchor = target?.closest('a[href]') as HTMLAnchorElement | null;
            if (!anchor) return;
            if (anchor.target && anchor.target !== '_self') return;
            if (anchor.hasAttribute('download')) return;

            const nextUrl = new URL(anchor.href, window.location.href);
            const currentUrl = new URL(window.location.href);
            if (nextUrl.href === currentUrl.href) return;

            if (!confirmDiscardChanges()) {
                event.preventDefault();
                event.stopPropagation();
            }
        };

        window.addEventListener('beforeunload', handleBeforeUnload);
        document.addEventListener('click', handleDocumentClick, true);

        return () => {
            window.removeEventListener('beforeunload', handleBeforeUnload);
            document.removeEventListener('click', handleDocumentClick, true);
        };
    }, [confirmDiscardChanges]);

    const handleNew = () => {
        if (!confirmDiscardChanges()) return;
        setSelectedId(null);
        form.resetFields();
        form.setFieldsValue(DEFAULT_FORM_VALUES);
        setIsDraftActive(true);
        setDirtyState(true);
    };

    const handleSelectCharacter = (characterId: number) => {
        if (characterId === selectedId) return;
        if (!confirmDiscardChanges()) return;
        setSelectedId(characterId);
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
                setDirtyState(false);
                notification.success({ message: 'Saved' });
            } else {
                const created = await fetchApi<Character>(API_SANDBOX.CHAT_APP_CHARACTER_CREATE, payload);
                setCharacters((prev) => [created, ...prev]);
                setSelectedId(created.id);
                setIsDraftActive(false);
                setDirtyState(false);
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
            setIsDraftActive(false);
            setDirtyState(false);
            notification.success({ message: 'Deleted' });
        } catch (error) {
            notification.error({
                message: 'Error',
                description: getErrorMessage(error, 'Failed to delete character'),
            });
        }
    };

    const handleStartChat = async (character: Character) => {
        if (!confirmDiscardChanges()) return;
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

    const handleDevPublishNow = async (character: Character) => {
        if (!confirmDiscardChanges()) return;
        setSelectedId(character.id);
        setDevPublishingCharacterId(character.id);
        try {
            await fetchApi<void>(
                API_SANDBOX.PERSONA_FEED_DEV_PUBLISH_NOW,
                {},
                { personaId: character.id },
            );
            notification.success({ message: 'Dev post triggered' });
            router.push(`/chat-app/persona/${character.id}`);
        } catch (error) {
            notification.error({
                message: 'Error',
                description: getErrorMessage(error, 'Failed to trigger dev post'),
            });
        } finally {
            setDevPublishingCharacterId(null);
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
                devPublishingCharacterId={devPublishingCharacterId}
                onNew={handleNew}
                onSelect={handleSelectCharacter}
                onStartChat={handleStartChat}
                onDevPublishNow={handleDevPublishNow}
            />
            <CharacterEditor
                form={form}
                selectedCharacter={selectedCharacter}
                isSaving={isSaving}
                onSave={handleSave}
                onDelete={handleDelete}
                onValuesChange={() => {
                    if (selectedId === null) setIsDraftActive(true);
                    setDirtyState(true);
                }}
            />
        </div>
    );
}
