'use client';

import type { BattleOutcome } from '@/types/battle3d';
import { Button } from '@/components/ui/button';

interface VictoryOverlayProps {
    outcome: BattleOutcome;
    attackerName: string;
    defenderName: string;
    onClose: () => void;
    onReplay: () => void;
}

export function VictoryOverlay({ outcome, attackerName, defenderName, onClose, onReplay }: VictoryOverlayProps) {
    const attackerWon = outcome.winner === 'attacker';
    const winnerName = attackerWon ? attackerName : defenderName;

    return (
        <div
            className="absolute inset-0 flex items-center justify-center z-20"
            style={{ animation: 'fadeIn 0.4s ease-out' }}
        >
            <div className="absolute inset-0 bg-black/60" />
            <div className="relative z-10 flex flex-col items-center gap-4 bg-gray-900/95 border border-gray-700 rounded-xl px-10 py-8 shadow-2xl">
                <div className={`text-5xl font-bold ${attackerWon ? 'text-yellow-400' : 'text-red-400'}`}>
                    {attackerWon ? '승리!' : '패배!'}
                </div>
                <div className="text-lg text-white font-semibold">{winnerName}</div>
                <div className="grid grid-cols-2 gap-6 text-sm mt-2">
                    <div className="text-center">
                        <div className="text-gray-400 text-xs mb-1">{attackerName} 잔여 병력</div>
                        <div className="text-white font-bold tabular-nums text-base">
                            {outcome.attackerRemaining.toLocaleString()}
                        </div>
                    </div>
                    <div className="text-center">
                        <div className="text-gray-400 text-xs mb-1">{defenderName} 잔여 병력</div>
                        <div className="text-white font-bold tabular-nums text-base">
                            {outcome.defenderRemaining.toLocaleString()}
                        </div>
                    </div>
                </div>
                {outcome.cityOccupied && <div className="text-xs text-orange-400 font-medium">도시 점령!</div>}
                <div className="flex gap-3 mt-2">
                    <Button variant="outline" size="sm" onClick={onReplay}>
                        다시 보기
                    </Button>
                    <Button size="sm" onClick={onClose}>
                        닫기
                    </Button>
                </div>
            </div>
            <style>{`
                @keyframes fadeIn {
                    from { opacity: 0; transform: scale(0.95); }
                    to { opacity: 1; transform: scale(1); }
                }
            `}</style>
        </div>
    );
}
