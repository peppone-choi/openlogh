'use client';

import { useState } from 'react';
import { cn } from '@/lib/utils';

export type SpecialOrderId = 'inspire_fleet' | 'comm_jam' | 'decoy_fleet' | 'fortress_cannon';

interface SpecialOrderDef {
    id: SpecialOrderId;
    label: string;
    sublabel: string;
    costType: 'military' | 'intel';
    cost: number;
    icon: string;
    effect: string;
    description: string;
    fortressOnly?: boolean;
}

const SPECIAL_ORDERS: SpecialOrderDef[] = [
    {
        id: 'inspire_fleet',
        label: '함대격려',
        sublabel: 'Inspire Fleet',
        costType: 'military',
        cost: 500,
        icon: '◆',
        effect: '사기 +15',
        description: '함대 전체의 사기를 고취. 즉각적인 전의 회복 효과.',
    },
    {
        id: 'comm_jam',
        label: '통신방해',
        sublabel: 'Comm Jam',
        costType: 'intel',
        cost: 2000,
        icon: '⊗',
        effect: '적 함대 1턴 마비',
        description: '적 1개 함대의 통신 계통을 교란. 1턴간 명령 불능.',
    },
    {
        id: 'decoy_fleet',
        label: '위장함대',
        sublabel: 'Decoy Fleet',
        costType: 'intel',
        cost: 1000,
        icon: '◇',
        effect: '허위 함대 1개 생성',
        description: '허위 함대를 생성하여 적의 주의를 분산.',
    },
    {
        id: 'fortress_cannon',
        label: '요새포발사',
        sublabel: 'Fortress Cannon',
        costType: 'military',
        cost: 0,
        icon: '⬟',
        effect: '광역 대미지',
        description: '이제르론 요새포 발사. 전방 광역 대미지. 요새 거점 전용.',
        fortressOnly: true,
    },
];

interface SpecialOrderPanelProps {
    militaryOps: number;
    intelOps: number;
    hasFortress?: boolean;
    onUse: (id: SpecialOrderId) => void;
}

export function SpecialOrderPanel({ militaryOps, intelOps, hasFortress = false, onUse }: SpecialOrderPanelProps) {
    const [confirmId, setConfirmId] = useState<SpecialOrderId | null>(null);

    const canAfford = (order: SpecialOrderDef): boolean => {
        if (order.fortressOnly && !hasFortress) return false;
        const pts = order.costType === 'military' ? militaryOps : intelOps;
        return pts >= order.cost;
    };

    const handleConfirm = () => {
        if (confirmId) {
            onUse(confirmId);
            setConfirmId(null);
        }
    };

    return (
        <div className="space-y-1.5">
            <div className="flex items-center justify-between">
                <span className="text-[10px] font-mono text-amber-500/70 tracking-widest uppercase">
                    특수 명령 // Special
                </span>
                <div className="flex items-center gap-2">
                    <span className="text-[8px] font-mono text-red-400/70 tabular-nums">
                        군사 {militaryOps.toLocaleString()}
                    </span>
                    <span className="text-[8px] font-mono text-sky-400/70 tabular-nums">
                        정보 {intelOps.toLocaleString()}
                    </span>
                </div>
            </div>

            <div className="space-y-1">
                {SPECIAL_ORDERS.map((order) => {
                    const affordable = canAfford(order);
                    const confirming = confirmId === order.id;
                    const isMilCost = order.costType === 'military';
                    const costColor = isMilCost ? 'text-red-400/80' : 'text-sky-400/80';
                    const costLabel = isMilCost ? '군사공작' : '정보공작';

                    if (confirming) {
                        return (
                            <div
                                key={order.id}
                                className="flex items-center gap-1.5 bg-amber-900/15 border border-amber-700/40 rounded px-2 py-1.5"
                            >
                                <span className="text-[9px] font-mono text-amber-400 flex-1 truncate">
                                    {order.label} 실행?
                                </span>
                                <button
                                    type="button"
                                    onClick={handleConfirm}
                                    className="text-[8px] font-mono text-emerald-400 border border-emerald-900/50 px-1.5 py-0.5 rounded hover:bg-emerald-900/20 transition-all shrink-0"
                                >
                                    확인
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setConfirmId(null)}
                                    className="text-[8px] font-mono text-gray-500 border border-gray-700/50 px-1.5 py-0.5 rounded hover:bg-gray-800/40 transition-all shrink-0"
                                >
                                    취소
                                </button>
                            </div>
                        );
                    }

                    return (
                        <button
                            key={order.id}
                            type="button"
                            disabled={!affordable}
                            title={order.description}
                            onClick={() => affordable && setConfirmId(order.id)}
                            className={cn(
                                'w-full flex items-center gap-2 px-2 py-1.5 rounded border transition-all duration-150 text-left',
                                affordable
                                    ? 'border-gray-700/50 bg-gray-900/40 hover:border-amber-700/40 hover:bg-gray-800/40'
                                    : 'border-gray-800/30 bg-gray-900/20 opacity-40 cursor-not-allowed'
                            )}
                        >
                            <span className="text-base leading-none w-5 text-center text-gray-500 shrink-0 font-mono">
                                {order.icon}
                            </span>
                            <div className="flex-1 min-w-0">
                                <div className="flex items-center gap-1.5">
                                    <span className="text-[10px] font-mono font-bold text-gray-300 leading-tight">
                                        {order.label}
                                    </span>
                                    {order.fortressOnly && (
                                        <span className="text-[7px] font-mono text-gray-600 border border-gray-700/40 px-0.5 py-px rounded">
                                            요새
                                        </span>
                                    )}
                                </div>
                                <div className="text-[8px] text-gray-600 leading-tight">{order.effect}</div>
                            </div>
                            <div className="text-right shrink-0 space-y-0.5">
                                {order.cost > 0 ? (
                                    <>
                                        <div className={cn('text-[9px] font-mono font-bold tabular-nums', costColor)}>
                                            {order.cost.toLocaleString()}
                                        </div>
                                        <div className="text-[7px] text-gray-700 leading-tight">{costLabel}</div>
                                    </>
                                ) : (
                                    <div className="text-[8px] font-mono text-gray-700">무료</div>
                                )}
                                {!affordable && <div className="text-[7px] font-mono text-red-500/70">부족</div>}
                            </div>
                        </button>
                    );
                })}
            </div>
        </div>
    );
}
