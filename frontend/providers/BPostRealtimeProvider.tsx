'use client';

import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { createContext, useContext, useEffect, useMemo, useRef } from 'react';
import { useSessionStore } from '@/stores/sessionStore';
import { useBPostStore } from '@/stores/bpostStore';
import { MessageDto, NotificationDto, PresenceEvent } from '@/interface/BPost';

type Ctx = {
    publish: (destination: string, body: unknown) => void;
};
const RealtimeCtx = createContext<Ctx>({ publish: () => {} });

const WS_PATH = `${process.env.NEXT_PUBLIC_API_URL || ''}/v1/api/b-post/ws`;

export function BPostRealtimeProvider({ children }: { children: React.ReactNode }) {
    const accessToken = useSessionStore((s) => s.accessToken);
    const internalUserId = useSessionStore((s) => s.internalUserId);

    const setConnected = useBPostStore((s) => s.setConnected);
    const onlineMark = useBPostStore((s) => s.onlineMark);
    const pushNotification = useBPostStore((s) => s.pushNotification);
    const appendMessage = useBPostStore((s) => s.appendMessage);
    const upsertConversationLast = useBPostStore((s) => s.upsertConversationLast);

    const clientRef = useRef<Client | null>(null);

    useEffect(() => {
        if (!accessToken || internalUserId == null) return;

        const client = new Client({
            webSocketFactory: () => new SockJS(WS_PATH) as unknown as WebSocket,
            connectHeaders: { Authorization: `Bearer ${accessToken}` },
            reconnectDelay: 4000,
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,
            debug: () => {},
        });

        client.onConnect = () => {
            setConnected(true);

            client.subscribe('/user/queue/messages', (frame: IMessage) => {
                try {
                    const msg = JSON.parse(frame.body) as MessageDto;
                    appendMessage(msg);
                    upsertConversationLast(msg, internalUserId);
                } catch (e) {
                    console.warn('[bpost-ws] bad message frame', e);
                }
            });

            client.subscribe('/user/queue/notifications', (frame: IMessage) => {
                try {
                    pushNotification(JSON.parse(frame.body) as NotificationDto);
                } catch (e) {
                    console.warn('[bpost-ws] bad notification frame', e);
                }
            });

            client.subscribe('/topic/presence', (frame: IMessage) => {
                try {
                    const ev = JSON.parse(frame.body) as PresenceEvent;
                    onlineMark(ev.userId, ev.online);
                } catch (e) {
                    console.warn('[bpost-ws] bad presence frame', e);
                }
            });
        };
        client.onStompError = (f) => console.error('[bpost-ws] STOMP error', f.headers['message']);
        client.onDisconnect = () => setConnected(false);
        client.onWebSocketClose = () => setConnected(false);

        clientRef.current = client;
        client.activate();

        return () => {
            void client.deactivate();
            clientRef.current = null;
            setConnected(false);
        };
    }, [accessToken, internalUserId, setConnected, onlineMark, pushNotification, appendMessage, upsertConversationLast]);

    const ctxValue = useMemo<Ctx>(
        () => ({
            publish: (destination, body) => {
                const c = clientRef.current;
                if (c && c.connected) {
                    c.publish({ destination, body: JSON.stringify(body) });
                }
            },
        }),
        []
    );

    return <RealtimeCtx.Provider value={ctxValue}>{children}</RealtimeCtx.Provider>;
}

export function useBPostRealtime() {
    return useContext(RealtimeCtx);
}
