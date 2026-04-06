'use client';

import { useState, useEffect } from 'react';
import { useChatStore } from '@/stores/chatStore';
import type { AddressBookEntry } from '@/types/chat';
import { Mail, Search, Inbox, Send, Trash2 } from 'lucide-react';

interface MailPanelProps {
    sessionId: number;
    officerId: number;
}

type MailTab = 'inbox' | 'compose';

export function MailPanel({ sessionId, officerId }: MailPanelProps) {
    const { mailCount, addressBook, loadMailCount, loadAddressBook, searchOfficers } = useChatStore();
    const [activeTab, setActiveTab] = useState<MailTab>('inbox');
    const [searchQuery, setSearchQuery] = useState('');
    const [searchResults, setSearchResults] = useState<AddressBookEntry[]>([]);
    const [selectedRecipient, setSelectedRecipient] = useState<AddressBookEntry | null>(null);
    const [composeContent, setComposeContent] = useState('');

    useEffect(() => {
        loadMailCount(sessionId, officerId);
        loadAddressBook(sessionId, officerId);
    }, [sessionId, officerId, loadMailCount, loadAddressBook]);

    const handleSearch = async () => {
        if (searchQuery.trim().length < 1) return;
        const results = await searchOfficers(sessionId, searchQuery.trim());
        setSearchResults(results);
    };

    const handleCompose = () => {
        if (!selectedRecipient || !composeContent.trim()) return;
        // Mail sending would use the existing message API
        setComposeContent('');
        setSelectedRecipient(null);
        setActiveTab('inbox');
    };

    return (
        <div className="bg-slate-900 border border-slate-700 rounded-lg overflow-hidden">
            {/* Tab header */}
            <div className="flex border-b border-slate-700">
                <button
                    onClick={() => setActiveTab('inbox')}
                    className={`flex-1 py-2 text-xs font-medium flex items-center justify-center gap-1 ${
                        activeTab === 'inbox' ? 'bg-slate-700 text-white' : 'text-slate-400 hover:text-slate-200'
                    }`}
                >
                    <Inbox className="size-3" />
                    수신함
                    {mailCount && (
                        <span className="ml-1 px-1.5 py-0.5 bg-blue-600 rounded-full text-[10px]">
                            {mailCount.private}/{mailCount.privateMax}
                        </span>
                    )}
                </button>
                <button
                    onClick={() => setActiveTab('compose')}
                    className={`flex-1 py-2 text-xs font-medium flex items-center justify-center gap-1 ${
                        activeTab === 'compose' ? 'bg-slate-700 text-white' : 'text-slate-400 hover:text-slate-200'
                    }`}
                >
                    <Send className="size-3" />
                    작성
                </button>
            </div>

            {/* Content */}
            <div className="p-3 min-h-[200px]">
                {activeTab === 'inbox' && (
                    <div className="space-y-2">
                        <div className="flex gap-2 text-xs text-slate-400">
                            <span>개인: {mailCount?.private ?? 0}</span>
                            <span>진영: {mailCount?.national ?? 0}</span>
                            <span>외교: {mailCount?.diplomacy ?? 0}</span>
                        </div>
                        <p className="text-xs text-slate-500 text-center py-8">
                            기존 메시지 시스템과 연동됩니다
                        </p>
                    </div>
                )}

                {activeTab === 'compose' && (
                    <div className="space-y-3">
                        {/* Recipient search */}
                        <div>
                            <label className="text-xs text-slate-400 mb-1 block">수신자</label>
                            {selectedRecipient ? (
                                <div className="flex items-center justify-between bg-slate-800 rounded px-2 py-1">
                                    <span className="text-sm text-white">{selectedRecipient.name}</span>
                                    <button
                                        onClick={() => setSelectedRecipient(null)}
                                        className="text-slate-500 hover:text-red-400"
                                    >
                                        <Trash2 className="size-3" />
                                    </button>
                                </div>
                            ) : (
                                <div className="space-y-1">
                                    <div className="flex gap-1">
                                        <input
                                            type="text"
                                            value={searchQuery}
                                            onChange={(e) => setSearchQuery(e.target.value)}
                                            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                                            placeholder="이름 검색..."
                                            className="flex-1 bg-slate-800 px-2 py-1 rounded text-sm text-white outline-none"
                                        />
                                        <button
                                            onClick={handleSearch}
                                            className="px-2 bg-slate-700 hover:bg-slate-600 rounded"
                                        >
                                            <Search className="size-3 text-slate-300" />
                                        </button>
                                    </div>
                                    {/* Address book + search results */}
                                    <div className="max-h-32 overflow-y-auto space-y-0.5">
                                        {(searchResults.length > 0 ? searchResults : addressBook).map((entry) => (
                                            <button
                                                key={entry.officerId}
                                                onClick={() => {
                                                    setSelectedRecipient(entry);
                                                    setSearchResults([]);
                                                    setSearchQuery('');
                                                }}
                                                className="w-full text-left px-2 py-1 text-xs text-slate-300 hover:bg-slate-700 rounded flex justify-between"
                                            >
                                                <span>{entry.name}</span>
                                                <span className="text-slate-500">{entry.factionName}</span>
                                            </button>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>

                        {/* Message content */}
                        <div>
                            <label className="text-xs text-slate-400 mb-1 block">내용</label>
                            <textarea
                                value={composeContent}
                                onChange={(e) => setComposeContent(e.target.value)}
                                placeholder="메시지 내용..."
                                className="w-full bg-slate-800 px-2 py-1 rounded text-sm text-white outline-none resize-none h-20"
                                maxLength={500}
                            />
                        </div>

                        <button
                            onClick={handleCompose}
                            disabled={!selectedRecipient || !composeContent.trim()}
                            className="w-full py-2 bg-blue-600 hover:bg-blue-500 disabled:bg-slate-700 disabled:text-slate-500 text-white text-sm rounded transition-colors flex items-center justify-center gap-1"
                        >
                            <Mail className="size-3" />
                            전송
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
}
