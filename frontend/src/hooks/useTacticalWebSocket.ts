'use client';

import { useEffect, useRef, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import { useBattleStore } from '@/stores/battleStore';

function buildBrokerURL(): string {
    const base = process.env.NEXT_PUBLIC_WS_URL;
    if (base) return base.replace(/^http/, 'ws') + '/ws-stomp';
    if (typeof window !== 'undefined') {
        const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        return `${proto}//${window.location.host}/ws-stomp`;
    }
    return 'ws://localhost:8080/ws-stomp';
}

interface TacticalWebSocketActions {
    sendOrder: (order: Record<string, unknown>) => void;
    sendReady: () => void;
    sendSetup: (setup: Record<string, unknown>) => void;
    connected: boolean;
}

export function useTacticalWebSocket(sessionCode: string | null): TacticalWebSocketActions {
    const clientRef = useRef<Client | null>(null);
    const connectedRef = useRef(false);

    const onStateUpdate = useBattleStore((s) => s.onStateUpdate);
    const onTurnResult = useBattleStore((s) => s.onTurnResult);
    const onTimerUpdate = useBattleStore((s) => s.onTimerUpdate);
    const onVictory = useBattleStore((s) => s.onVictory);

    useEffect(() => {
        if (!sessionCode || sessionCode.startsWith('demo')) return;

        const client = new Client({
            brokerURL: buildBrokerURL(),
            reconnectDelay: 5000,
            onConnect: () => {
                connectedRef.current = true;

                client.subscribe(`/topic/tactical/${sessionCode}/state`, (msg) => {
                    onStateUpdate(JSON.parse(msg.body));
                });

                client.subscribe(`/topic/tactical/${sessionCode}/turn-result`, (msg) => {
                    onTurnResult(JSON.parse(msg.body));
                });

                client.subscribe(`/topic/tactical/${sessionCode}/timer`, (msg) => {
                    onTimerUpdate(JSON.parse(msg.body).timer ?? 30);
                });

                client.subscribe(`/topic/tactical/${sessionCode}/victory`, (msg) => {
                    onVictory(JSON.parse(msg.body));
                });
            },
            onDisconnect: () => {
                connectedRef.current = false;
            },
        });

        client.activate();
        clientRef.current = client;

        return () => {
            client.deactivate();
            clientRef.current = null;
            connectedRef.current = false;
        };
    }, [sessionCode, onStateUpdate, onTurnResult, onTimerUpdate, onVictory]);

    const sendOrder = useCallback(
        (order: Record<string, unknown>) => {
            if (!clientRef.current?.active || !sessionCode) return;
            clientRef.current.publish({
                destination: `/app/tactical/${sessionCode}/order`,
                body: JSON.stringify(order),
            });
        },
        [sessionCode]
    );

    const sendReady = useCallback(() => {
        if (!clientRef.current?.active || !sessionCode) return;
        clientRef.current.publish({
            destination: `/app/tactical/${sessionCode}/ready`,
            body: JSON.stringify({ ready: true }),
        });
    }, [sessionCode]);

    const sendSetup = useCallback(
        (setup: Record<string, unknown>) => {
            if (!clientRef.current?.active || !sessionCode) return;
            clientRef.current.publish({
                destination: `/app/tactical/${sessionCode}/setup`,
                body: JSON.stringify(setup),
            });
        },
        [sessionCode]
    );

    return { sendOrder, sendReady, sendSetup, connected: connectedRef.current };
}
