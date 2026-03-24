'use client';

import { useMemo } from 'react';
import { Eye, EyeOff, Radar } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { useBattleStore } from '@/stores/battleStore';

/**
 * ReconOverlay - Shows sensor/reconnaissance information in the battle UI.
 *
 * Displays:
 * - Sensor range indicator based on SENSOR energy allocation
 * - Detected vs undetected enemy units (fog of war)
 * - Auto-detection indicator for stationary units
 */
export function ReconOverlay() {
    const { pendingEnergy, enemyFleets, myFleets, tacticalUnits } = useBattleStore();

    const sensorPct =
        pendingEnergy.sensor ??
        Math.max(
            0,
            100 -
                pendingEnergy.beam -
                pendingEnergy.gun -
                pendingEnergy.shield -
                pendingEnergy.engine -
                pendingEnergy.warp
        );
    const sensorRange = Math.round(sensorPct * 0.2); // 0-20 cells range based on sensor %

    // Determine detected vs undetected enemies
    const { detected, undetected } = useMemo(() => {
        if (myFleets.length === 0) return { detected: 0, undetected: 0 };

        const myFleet = myFleets[0];
        const myCenterX =
            myFleet.units.length > 0
                ? myFleet.units.reduce((s, u) => s + (u.gridX ?? 0), 0) / myFleet.units.length
                : 10;
        const myCenterY =
            myFleet.units.length > 0
                ? myFleet.units.reduce((s, u) => s + (u.gridY ?? 0), 0) / myFleet.units.length
                : 10;

        let det = 0;
        let undet = 0;

        for (const fleet of enemyFleets) {
            for (const unit of fleet.units) {
                const dx = (unit.gridX ?? 0) - myCenterX;
                const dy = (unit.gridY ?? 0) - myCenterY;
                const dist = Math.sqrt(dx * dx + dy * dy);
                if (dist <= sensorRange) {
                    det++;
                } else {
                    undet++;
                }
            }
        }

        return { detected: det, undetected: undet };
    }, [myFleets, enemyFleets, sensorRange]);

    // Stationary unit auto-detection
    const stationaryAutoDetected = useMemo(() => {
        // Units that haven't moved are auto-detected at longer range
        return tacticalUnits.filter((u) => !u.isMyUnit).length > 0;
    }, [tacticalUnits]);

    return (
        <div className="space-y-2">
            <div className="flex items-center gap-2">
                <Radar className="size-3.5 text-violet-400" />
                <span className="text-[10px] font-mono text-violet-400 uppercase tracking-wider">
                    색적 정보 // Recon
                </span>
            </div>

            <div className="grid grid-cols-3 gap-2 text-center">
                <div className="bg-violet-950/20 rounded p-1.5">
                    <div className="text-[9px] text-muted-foreground">센서 범위</div>
                    <div className="text-sm font-mono text-violet-300 tabular-nums">{sensorRange}셀</div>
                </div>
                <div className="bg-green-950/20 rounded p-1.5">
                    <div className="text-[9px] text-muted-foreground flex items-center justify-center gap-0.5">
                        <Eye className="size-2.5" />
                        탐지
                    </div>
                    <div className="text-sm font-mono text-green-400 tabular-nums">{detected}</div>
                </div>
                <div className="bg-red-950/20 rounded p-1.5">
                    <div className="text-[9px] text-muted-foreground flex items-center justify-center gap-0.5">
                        <EyeOff className="size-2.5" />
                        미탐지
                    </div>
                    <div className="text-sm font-mono text-red-400 tabular-nums">{undetected}</div>
                </div>
            </div>

            {stationaryAutoDetected && (
                <div className="flex items-center gap-1.5 text-[10px] text-amber-400/70">
                    <Badge variant="outline" className="text-[8px] px-1 py-0 border-amber-400/30 text-amber-400/70">
                        자동 감지
                    </Badge>
                    정지 유닛은 확장 범위에서 자동 탐지됩니다
                </div>
            )}
        </div>
    );
}
