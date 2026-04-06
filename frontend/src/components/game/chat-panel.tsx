'use client';

import { useState, useRef, useEffect } from 'react';
import { useChatStore, scopeKey } from '@/stores/chatStore';
import type { ChatScope } from '@/types/chat';
import { CHAT_SCOPE_LABELS } from '@/types/chat';
import { Send, MessageSquare } from 'lucide-react';

interface ChatPanelProps {
    sessionId: number;
    officerId: number;
    planetId: number;
    factionId: number;
    onSend?: (content: string, scope: ChatScope) => void;
}

export function ChatPanel({ sessionId, officerId, planetId, factionId, onSend }: ChatPanelProps) {
    const { messages, currentScope, switchScope, loadHistory } = useChatStore();
    const [input, setInput] = useState('');
    const [lastSentTime, setLastSentTime] = useState(0);
    const [collapsed, setCollapsed] = useState(false);
    const messagesEndRef = useRef<HTMLDivElement>(null);

    const scopes: { scope: ChatScope; scopeId: number }[] = [
        { scope: 'PLANET', scopeId: planetId },
        { scope: 'FACTION', scopeId: factionId },
        { scope: 'GLOBAL', scopeId: sessionId },
    ];

    const currentScopeId = scopes.find(s => s.scope === currentScope)?.scopeId ?? sessionId;
    const key = scopeKey(currentScope, currentScopeId);
    const currentMessages = messages[key] ?? [];

    useEffect(() => {
        loadHistory(sessionId, currentScope, currentScopeId);
    }, [sessionId, currentScope, currentScopeId, loadHistory]);

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [currentMessages.length]);

    const handleSend = () => {
        const now = Date.now();
        if (now - lastSentTime < 2000) return;
        if (!input.trim()) return;

        onSend?.(input.trim(), currentScope);
        setInput('');
        setLastSentTime(now);
    };

    const canSend = Date.now() - lastSentTime >= 2000 && input.trim().length > 0;

    if (collapsed) {
        return (
            <button
                onClick={() => setCollapsed(false)}
                className="fixed bottom-4 right-4 z-40 bg-slate-800 hover:bg-slate-700 text-white p-3 rounded-full shadow-lg"
            >
                <MessageSquare className="size-5" />
            </button>
        );
    }

    return (
        <div className="flex flex-col h-80 bg-slate-900 border border-slate-700 rounded-lg overflow-hidden">
            {/* Scope tabs */}
            <div className="flex border-b border-slate-700">
                {scopes.map(({ scope }) => (
                    <button
                        key={scope}
                        onClick={() => switchScope(scope)}
                        className={`flex-1 py-2 text-xs font-medium transition-colors ${
                            currentScope === scope
                                ? 'bg-slate-700 text-white'
                                : 'text-slate-400 hover:text-slate-200'
                        }`}
                    >
                        {CHAT_SCOPE_LABELS[scope]}
                    </button>
                ))}
                <button
                    onClick={() => setCollapsed(true)}
                    className="px-2 text-slate-500 hover:text-slate-300 text-xs"
                >
                    _
                </button>
            </div>

            {/* Messages */}
            <div className="flex-1 overflow-y-auto p-2 space-y-1">
                {currentMessages.map((msg) => (
                    <div key={msg.id} className="text-xs">
                        <span className="font-medium text-blue-400">{msg.senderName}</span>
                        <span className="text-slate-500 mx-1">:</span>
                        <span className="text-slate-300">{msg.content}</span>
                    </div>
                ))}
                <div ref={messagesEndRef} />
            </div>

            {/* Input */}
            <div className="flex border-t border-slate-700">
                <input
                    type="text"
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleSend()}
                    placeholder="메시지 입력..."
                    className="flex-1 bg-transparent px-3 py-2 text-sm text-white outline-none"
                    maxLength={200}
                />
                <button
                    onClick={handleSend}
                    disabled={!canSend}
                    className="px-3 text-blue-400 hover:text-blue-300 disabled:text-slate-600 transition-colors"
                >
                    <Send className="size-4" />
                </button>
            </div>
        </div>
    );
}
