'use client';

import { useEffect } from 'react';
import type { BattleOrder } from '@/stores/battleStore';
import { cn } from '@/lib/utils';

interface OrderDef {
    id: BattleOrder;
    label: string;
    sublabel: string;
    shortcut: number;
    color: string;
    description: string;
    validUnitTypes: string[];
}

const ORDERS: OrderDef[] = [
    {
        id: 'breakthrough',
        label: '돌파',
        sublabel: 'Break Through',
        shortcut: 1,
        color: 'text-red-400 border-red-900/60 hover:border-red-500/60 hover:bg-red-900/20',
        description: '전력으로 적진을 돌파. 피해를 감수하고 전선을 돌파. 방추형 진형에서 효과 증가.',
        validUnitTypes: ['fleet', 'patrol'],
    },
    {
        id: 'pin_down',
        label: '견제',
        sublabel: 'Pin Down',
        shortcut: 2,
        color: 'text-amber-400 border-amber-900/60 hover:border-amber-500/60 hover:bg-amber-900/20',
        description: '적 함대를 견제하며 교전을 유지. 아군의 기동 지원에 효과적.',
        validUnitTypes: ['fleet', 'patrol', 'transport'],
    },
    {
        id: 'flank',
        label: '우회',
        sublabel: 'Flank',
        shortcut: 3,
        color: 'text-sky-400 border-sky-900/60 hover:border-sky-500/60 hover:bg-sky-900/20',
        description: '적 측면으로 우회 기동. 사선진에서 효과 증가. 기관 출력 필요.',
        validUnitTypes: ['fleet', 'patrol'],
    },
    {
        id: 'retreat',
        label: '퇴각',
        sublabel: 'Retreat',
        shortcut: 4,
        color: 'text-gray-400 border-gray-700/60 hover:border-gray-500/60 hover:bg-gray-800/20',
        description: '전장에서 후방으로 철수. 기관 출력 집중으로 피해를 최소화.',
        validUnitTypes: ['fleet', 'patrol', 'transport', 'hospital'],
    },
    {
        id: 'hold',
        label: '방어',
        sublabel: 'Hold Position',
        shortcut: 5,
        color: 'text-emerald-400 border-emerald-900/60 hover:border-emerald-500/60 hover:bg-emerald-900/20',
        description: '현 위치 사수. 방진 진형과 조합 시 방어력 대폭 증가.',
        validUnitTypes: ['fleet', 'patrol', 'transport', 'hospital', 'fortress'],
    },
    {
        id: 'pursue',
        label: '추격',
        sublabel: 'Pursue',
        shortcut: 6,
        color: 'text-violet-400 border-violet-900/60 hover:border-violet-500/60 hover:bg-violet-900/20',
        description: '퇴각하는 적 함대를 추격. 속도전으로 적의 조직적 후퇴를 방해.',
        validUnitTypes: ['fleet'],
    },
];

const ORDER_LABEL: Record<BattleOrder, string> = {
    breakthrough: '돌파',
    pin_down: '견제',
    flank: '우회',
    retreat: '퇴각',
    hold: '방어',
    pursue: '추격',
};

export interface QueuedOrder {
    unitId: string;
    unitName: string;
    order: BattleOrder;
}

interface OrderPanelProps {
    value: BattleOrder | null;
    onChange: (o: BattleOrder | null) => void;
    disabled?: boolean;
    disabledOrders?: BattleOrder[];
    orderQueue?: QueuedOrder[];
    onCancelQueueItem?: (index: number) => void;
    unitType?: string;
}

export function OrderPanel({
    value,
    onChange,
    disabled = false,
    disabledOrders = [],
    orderQueue = [],
    onCancelQueueItem,
    unitType = 'fleet',
}: OrderPanelProps) {
    useEffect(() => {
        if (disabled) return;
        const handler = (e: KeyboardEvent) => {
            if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return;
            const num = parseInt(e.key, 10);
            if (num >= 1 && num <= 6) {
                const order = ORDERS[num - 1];
                if (order && !disabledOrders.includes(order.id) && order.validUnitTypes.includes(unitType)) {
                    onChange(value === order.id ? null : order.id);
                }
            }
        };
        window.addEventListener('keydown', handler);
        return () => window.removeEventListener('keydown', handler);
    }, [disabled, disabledOrders, value, onChange, unitType]);

    return (
        <div className="space-y-1.5">
            <div className="text-[10px] font-mono text-amber-500/70 tracking-widest uppercase">작전 명령 // Orders</div>
            <div className="grid grid-cols-2 gap-1.5">
                {ORDERS.map((order) => {
                    const active = value === order.id;
                    const unavailable = !order.validUnitTypes.includes(unitType);
                    const isDisabled = disabled || disabledOrders.includes(order.id) || unavailable;
                    return (
                        <button
                            key={order.id}
                            type="button"
                            disabled={isDisabled}
                            title={order.description}
                            onClick={() => onChange(active ? null : order.id)}
                            className={cn(
                                'flex items-center gap-1.5 px-2 py-1.5 rounded border transition-all duration-150',
                                'text-left select-none relative group',
                                order.color,
                                active ? 'ring-1 ring-current bg-current/10 brightness-110' : '',
                                isDisabled && 'opacity-40 cursor-not-allowed'
                            )}
                        >
                            <span className="text-[9px] font-mono text-gray-700 w-3 shrink-0 tabular-nums">
                                {order.shortcut}
                            </span>
                            <div className="min-w-0 flex-1">
                                <div className="text-[11px] font-mono font-bold leading-tight">{order.label}</div>
                                <div className="text-[8px] text-gray-600 leading-tight truncate">{order.sublabel}</div>
                            </div>
                            {active && <span className="ml-auto text-[8px] font-mono opacity-70 shrink-0">◉</span>}
                            {unavailable && (
                                <span className="ml-auto text-[7px] font-mono text-gray-700 shrink-0">N/A</span>
                            )}
                            {/* Tooltip */}
                            <div className="absolute bottom-full left-0 mb-1 w-52 z-50 pointer-events-none opacity-0 group-hover:opacity-100 transition-opacity duration-150">
                                <div className="bg-gray-950 border border-gray-700/60 rounded p-2 text-[9px] text-gray-400 font-mono leading-relaxed shadow-xl">
                                    {order.description}
                                </div>
                            </div>
                        </button>
                    );
                })}
            </div>

            {/* Order queue */}
            {orderQueue.length > 0 && (
                <div className="space-y-1 pt-0.5">
                    <div className="text-[9px] font-mono text-gray-600 uppercase tracking-widest">
                        명령 대기열 ({orderQueue.length})
                    </div>
                    {orderQueue.map((item, idx) => (
                        <div
                            key={`${item.unitId}-${idx}`}
                            className="flex items-center justify-between bg-gray-900/40 border border-gray-800/50 rounded px-2 py-1"
                        >
                            <div className="flex items-center gap-1.5 min-w-0">
                                <span className="text-[8px] font-mono text-gray-700 shrink-0">[{idx + 1}]</span>
                                <span className="text-[9px] font-mono text-gray-400 truncate">{item.unitName}</span>
                                <span className="text-[9px] font-mono text-amber-400/80 shrink-0">
                                    → {ORDER_LABEL[item.order]}
                                </span>
                            </div>
                            {onCancelQueueItem && (
                                <button
                                    type="button"
                                    onClick={() => onCancelQueueItem(idx)}
                                    className="text-[9px] font-mono text-gray-700 hover:text-red-400 transition-colors ml-2 shrink-0 w-4 text-center"
                                >
                                    ×
                                </button>
                            )}
                        </div>
                    ))}
                </div>
            )}

            <div className="text-[8px] font-mono text-gray-700">단축키: 1–6</div>
        </div>
    );
}
