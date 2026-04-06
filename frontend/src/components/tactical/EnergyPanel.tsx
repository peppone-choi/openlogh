'use client';

import { useCallback } from 'react';
import type { EnergyAllocation } from '@/types/tactical';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';

interface EnergyPanelProps {
    energy: EnergyAllocation;
    onChange: (energy: EnergyAllocation) => void;
    disabled?: boolean;
}

const CHANNELS: { key: keyof EnergyAllocation; label: string; color: string }[] = [
    { key: 'beam', label: 'BEAM', color: 'bg-red-500' },
    { key: 'gun', label: 'GUN', color: 'bg-orange-500' },
    { key: 'shield', label: 'SHIELD', color: 'bg-blue-500' },
    { key: 'engine', label: 'ENGINE', color: 'bg-green-500' },
    { key: 'warp', label: 'WARP', color: 'bg-purple-500' },
    { key: 'sensor', label: 'SENSOR', color: 'bg-cyan-500' },
];

export function EnergyPanel({ energy, onChange, disabled = false }: EnergyPanelProps) {
    const handleChange = useCallback(
        (key: keyof EnergyAllocation, value: number) => {
            const oldValue = energy[key];
            const diff = value - oldValue;
            if (diff === 0) return;

            // Distribute the difference across other channels proportionally
            const otherKeys = CHANNELS.map((c) => c.key).filter((k) => k !== key);
            const otherTotal = otherKeys.reduce((sum, k) => sum + energy[k], 0);

            const newEnergy = { ...energy, [key]: value };

            if (otherTotal === 0) {
                // All other channels are 0, can't redistribute
                return;
            }

            let remaining = -diff;
            for (let i = 0; i < otherKeys.length; i++) {
                const k = otherKeys[i];
                if (i === otherKeys.length - 1) {
                    // Last channel gets the remainder to ensure sum = 100
                    newEnergy[k] = Math.max(0, energy[k] + remaining);
                } else {
                    const share = Math.round((energy[k] / otherTotal) * remaining);
                    const adjusted = Math.max(0, Math.min(100, energy[k] + share));
                    remaining -= adjusted - energy[k];
                    newEnergy[k] = adjusted;
                }
            }

            // Verify sum = 100
            const total = Object.values(newEnergy).reduce((a, b) => a + b, 0);
            if (total !== 100) {
                // Fix rounding by adjusting the last non-target channel
                const lastKey = otherKeys[otherKeys.length - 1];
                newEnergy[lastKey] += 100 - total;
            }

            // Clamp all values
            for (const k of CHANNELS.map((c) => c.key)) {
                newEnergy[k] = Math.max(0, Math.min(100, newEnergy[k]));
            }

            onChange(newEnergy);
        },
        [energy, onChange]
    );

    return (
        <Card>
            <CardHeader className="pb-2">
                <CardTitle className="text-sm">에너지 배분</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
                {CHANNELS.map(({ key, label, color }) => (
                    <div key={key} className="space-y-0.5">
                        <div className="flex justify-between text-xs">
                            <span className="font-mono text-muted-foreground">{label}</span>
                            <span className="tabular-nums w-8 text-right">{energy[key]}%</span>
                        </div>
                        <div className="flex items-center gap-2">
                            <div className="flex-1 h-3 bg-gray-800 rounded overflow-hidden">
                                <div
                                    className={`h-full ${color} transition-all duration-150`}
                                    style={{ width: `${energy[key]}%` }}
                                />
                            </div>
                            <input
                                type="range"
                                min={0}
                                max={100}
                                value={energy[key]}
                                onChange={(e) => handleChange(key, Number(e.target.value))}
                                disabled={disabled}
                                className="w-20 h-2 accent-white"
                            />
                        </div>
                    </div>
                ))}
                <div className="text-xs text-muted-foreground text-right pt-1 border-t border-gray-800">
                    합계: {Object.values(energy).reduce((a, b) => a + b, 0)}%
                </div>
            </CardContent>
        </Card>
    );
}
