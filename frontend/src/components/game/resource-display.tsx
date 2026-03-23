import { Coins, Package, Ship } from 'lucide-react';

interface ResourceDisplayProps {
    funds: number;
    supplies: number;
    ships: number;
}

export function ResourceDisplay({ funds, supplies, ships }: ResourceDisplayProps) {
    return (
        <div className="flex items-center gap-3 text-xs">
            <span className="flex items-center gap-1">
                <Coins className="size-3.5" style={{ color: 'var(--empire-gold)' }} />
                <span className="tabular-nums font-medium" style={{ color: 'var(--empire-gold)' }}>
                    {funds.toLocaleString()}
                </span>
            </span>
            <span className="flex items-center gap-1">
                <Package className="size-3.5 text-sky-400" />
                <span className="text-sky-400 tabular-nums font-medium">{supplies.toLocaleString()}</span>
            </span>
            <span className="flex items-center gap-1">
                <Ship className="size-3.5 text-blue-400" />
                <span className="text-blue-400 tabular-nums font-medium">{ships.toLocaleString()}</span>
            </span>
        </div>
    );
}
