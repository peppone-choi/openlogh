'use client';

import { Badge } from '@/components/ui/badge';

interface OriginSelectorProps {
    factionType: string;
    value: string;
    onChange: (value: string) => void;
}

const EMPIRE_ORIGINS = [
    { value: 'noble', label: '귀족' },
    { value: 'knight', label: '제국기사' },
    { value: 'commoner', label: '평민' },
] as const;

export function OriginSelector({ factionType, value, onChange }: OriginSelectorProps) {
    if (factionType !== 'empire') {
        return (
            <div className="space-y-1">
                <label className="text-sm text-muted-foreground">출신</label>
                <div className="flex items-center gap-2">
                    <Badge variant="secondary">시민</Badge>
                </div>
            </div>
        );
    }

    return (
        <div className="space-y-1">
            <label className="text-sm text-muted-foreground">출신</label>
            <div className="flex items-center gap-2">
                {EMPIRE_ORIGINS.map((origin) => (
                    <label
                        key={origin.value}
                        className="flex items-center gap-1.5 cursor-pointer"
                    >
                        <input
                            type="radio"
                            name="originType"
                            value={origin.value}
                            checked={value === origin.value}
                            onChange={() => onChange(origin.value)}
                            className="accent-[var(--primary)]"
                        />
                        <span className="text-sm">{origin.label}</span>
                    </label>
                ))}
            </div>
        </div>
    );
}
