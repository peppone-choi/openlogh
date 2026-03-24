'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { useGameStore } from '@/stores/gameStore';
import { subscribeWebSocket } from '@/lib/websocket';
import { messageApi } from '@/lib/gameApi';
import type { Officer, Message } from '@/types';
import { MessageCircle, Send, Circle } from 'lucide-react';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';

export default function MessengerPage() {
    const { currentWorld } = useWorldStore();
    const { myOfficer, fetchMyGeneral } = useOfficerStore();
    const { generals, loadAll } = useGameStore();

    const [selectedOfficer, setSelectedOfficer] = useState<Officer | null>(null);
    const [messages, setMessages] = useState<Message[]>([]);
    const [content, setContent] = useState('');
    const [loading, setLoading] = useState(true);
    const [sending, setSending] = useState(false);
    const [onlineIds] = useState<Set<number>>(new Set());
    const scrollRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!currentWorld) return;
        if (!myOfficer) fetchMyGeneral(currentWorld.id).catch(() => {});
        loadAll(currentWorld.id).finally(() => setLoading(false));
    }, [currentWorld, myOfficer, fetchMyGeneral, loadAll]);

    const factionOfficers = useMemo(() => {
        if (!myOfficer) return [];
        return generals.filter((g) => g.factionId === myOfficer.factionId && g.id !== myOfficer.id && g.factionId > 0);
    }, [generals, myOfficer]);

    const fetchConversation = useCallback(async () => {
        if (!currentWorld || !myOfficer || !selectedOfficer) return;
        try {
            const res = await messageApi.getByType('private', {
                generalId: myOfficer.id,
                limit: 50,
            });
            const conv = res.data.filter(
                (m) =>
                    (m.srcId === selectedOfficer.id && m.destId === myOfficer.id) ||
                    (m.srcId === myOfficer.id && m.destId === selectedOfficer.id)
            );
            setMessages(conv.sort((a, b) => new Date(a.sentAt).getTime() - new Date(b.sentAt).getTime()));
        } catch {
            /* ignore */
        }
    }, [currentWorld, myOfficer, selectedOfficer]);

    useEffect(() => {
        void fetchConversation();
    }, [fetchConversation]);

    useEffect(() => {
        if (!currentWorld || !myOfficer) return;
        const unsub = subscribeWebSocket(`/topic/world/${currentWorld.id}/message`, () => {
            void fetchConversation();
        });
        return unsub;
    }, [currentWorld, myOfficer, fetchConversation]);

    useEffect(() => {
        if (scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
    }, [messages]);

    const handleSend = async () => {
        if (!currentWorld || !myOfficer || !selectedOfficer || !content.trim()) return;
        setSending(true);
        try {
            await messageApi.send(currentWorld.id, myOfficer.id, selectedOfficer.id, content.trim(), {
                mailboxCode: 'personal',
                mailboxType: 'PRIVATE',
                messageType: 'personal',
                officerLevel: myOfficer.officerLevel,
            });
            setContent('');
            await fetchConversation();
        } finally {
            setSending(false);
        }
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
            void handleSend();
        }
    };

    if (!currentWorld) return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
    if (loading) return <LoadingState />;

    return (
        <div className="p-4 max-w-4xl mx-auto h-[calc(100vh-8rem)] flex flex-col gap-4">
            <PageHeader icon={MessageCircle} title="메신저" description="같은 진영 제독과의 1:1 실시간 대화" />

            <div className="flex gap-3 flex-1 min-h-0">
                {/* Officer list */}
                <div className="w-48 shrink-0">
                    <Card className="h-full">
                        <CardContent className="p-2 space-y-1">
                            <div className="text-xs text-muted-foreground px-1 py-0.5 font-medium">진영 제독</div>
                            {factionOfficers.length === 0 && (
                                <div className="text-xs text-muted-foreground px-1">같은 진영 제독 없음</div>
                            )}
                            {factionOfficers.map((o) => {
                                const isOnline = onlineIds.has(o.id);
                                const isSelected = selectedOfficer?.id === o.id;
                                return (
                                    <button
                                        key={o.id}
                                        type="button"
                                        onClick={() => setSelectedOfficer(o)}
                                        className={`w-full flex items-center gap-2 px-2 py-1.5 rounded text-left text-sm transition-colors ${
                                            isSelected
                                                ? 'bg-amber-500/20 text-amber-200'
                                                : 'hover:bg-muted/50 text-foreground'
                                        }`}
                                    >
                                        <Circle
                                            className={`size-2 shrink-0 ${isOnline ? 'fill-green-400 text-green-400' : 'fill-gray-600 text-gray-600'}`}
                                        />
                                        <span className="truncate">{o.name}</span>
                                    </button>
                                );
                            })}
                        </CardContent>
                    </Card>
                </div>

                {/* Chat area */}
                <div className="flex-1 flex flex-col min-w-0 gap-2">
                    {!selectedOfficer ? (
                        <Card className="flex-1 flex items-center justify-center">
                            <div className="text-muted-foreground text-sm">대화할 제독을 선택하세요.</div>
                        </Card>
                    ) : (
                        <>
                            <div className="flex items-center gap-2 px-1">
                                <Badge variant="outline" className="text-amber-300 border-amber-800">
                                    {selectedOfficer.name}
                                </Badge>
                                <span className="text-xs text-muted-foreground">와(과) 대화 중</span>
                            </div>

                            <Card className="flex-1 min-h-0">
                                <CardContent className="p-0 h-full">
                                    <ScrollArea className="h-full p-3" ref={scrollRef}>
                                        <div className="space-y-2">
                                            {messages.length === 0 && (
                                                <div className="text-center text-xs text-muted-foreground py-8">
                                                    대화 내역이 없습니다.
                                                </div>
                                            )}
                                            {messages.map((m) => {
                                                const isMine = m.srcId === myOfficer?.id;
                                                return (
                                                    <div
                                                        key={m.id}
                                                        className={`flex ${isMine ? 'justify-end' : 'justify-start'}`}
                                                    >
                                                        <div
                                                            className={`max-w-[75%] rounded-lg px-3 py-2 text-sm ${
                                                                isMine
                                                                    ? 'bg-amber-600/30 text-amber-100'
                                                                    : 'bg-muted text-foreground'
                                                            }`}
                                                        >
                                                            <p>
                                                                {(m.payload.content as string) ??
                                                                    (m.payload.text as string) ??
                                                                    JSON.stringify(m.payload)}
                                                            </p>
                                                            <p className="text-[10px] text-muted-foreground mt-0.5 text-right">
                                                                {new Date(m.sentAt).toLocaleTimeString('ko-KR', {
                                                                    hour: '2-digit',
                                                                    minute: '2-digit',
                                                                })}
                                                            </p>
                                                        </div>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    </ScrollArea>
                                </CardContent>
                            </Card>

                            <div className="flex gap-2">
                                <Textarea
                                    value={content}
                                    onChange={(e) => setContent(e.target.value)}
                                    onKeyDown={handleKeyDown}
                                    placeholder="메시지 입력... (Ctrl+Enter로 전송)"
                                    className="resize-none h-16 flex-1"
                                />
                                <Button
                                    onClick={() => void handleSend()}
                                    disabled={sending || !content.trim()}
                                    className="h-16 px-4"
                                >
                                    <Send className="size-4" />
                                </Button>
                            </div>
                        </>
                    )}
                </div>
            </div>
        </div>
    );
}
