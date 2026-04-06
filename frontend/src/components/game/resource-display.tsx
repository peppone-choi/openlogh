import { CircleDollarSign, Package, Ship } from 'lucide-react';

interface ResourceDisplayProps {
    gold: number;
    rice: number;
    crew: number;
}

/** 장교 자원 표시: 자금 / 물자 / 함선 */
export function ResourceDisplay({ gold, rice, crew }: ResourceDisplayProps) {
    return (
        <div className="flex items-center gap-3 text-xs text-muted-foreground">
            <span className="flex items-center gap-1" title="자금">
                <CircleDollarSign className="size-3.5 text-[#c9a84c]" />
                <span className="text-[#c9a84c] tabular-nums">{gold.toLocaleString()}</span>
            </span>
            <span className="flex items-center gap-1" title="물자">
                <Package className="size-3.5 text-[#00d4ff]" />
                <span className="text-[#00d4ff] tabular-nums">{rice.toLocaleString()}</span>
            </span>
            <span className="flex items-center gap-1" title="함선">
                <Ship className="size-3.5 text-blue-400" />
                <span className="text-blue-400 tabular-nums">{crew.toLocaleString()}</span>
            </span>
        </div>
    );
}
