import { Badge } from '@/components/ui/badge';
import { Ship } from 'lucide-react';

interface FleetLimits {
    /** Current fleet count */
    fleets: number;
    /** Max fleet count */
    maxFleets: number;
    /** Current patrol count */
    patrols: number;
    /** Max patrol count */
    maxPatrols: number;
    /** Current ground unit count */
    groundUnits: number;
    /** Max ground unit count */
    maxGroundUnits: number;
}

interface FleetLimitsBadgeProps {
    limits: FleetLimits;
    className?: string;
}

/**
 * Calculates fleet limits based on faction population (per gin7 §6.12).
 * Population thresholds determine how many fleets, patrols, and ground units are allowed.
 */
export function calcFleetLimits(
    totalPopulation: number
): Pick<FleetLimits, 'maxFleets' | 'maxPatrols' | 'maxGroundUnits'> {
    // gin7 §6.12: limits scale with population tiers
    const pop = Math.max(0, totalPopulation);
    let maxFleets: number;
    let maxPatrols: number;
    let maxGroundUnits: number;

    if (pop >= 10_000_000) {
        maxFleets = 10;
        maxPatrols = 50;
        maxGroundUnits = 50;
    } else if (pop >= 5_000_000) {
        maxFleets = 7;
        maxPatrols = 40;
        maxGroundUnits = 40;
    } else if (pop >= 2_000_000) {
        maxFleets = 5;
        maxPatrols = 30;
        maxGroundUnits = 30;
    } else if (pop >= 1_000_000) {
        maxFleets = 3;
        maxPatrols = 20;
        maxGroundUnits = 20;
    } else {
        maxFleets = 2;
        maxPatrols = 10;
        maxGroundUnits = 10;
    }

    return { maxFleets, maxPatrols, maxGroundUnits };
}

function LimitSegment({ label, current, max }: { label: string; current: number; max: number }) {
    const isFull = current >= max;
    return (
        <span className={`tabular-nums ${isFull ? 'text-red-400' : 'text-foreground'}`}>
            {label}: {current}/{max}
        </span>
    );
}

export function FleetLimitsBadge({ limits, className }: FleetLimitsBadgeProps) {
    return (
        <div className={`flex items-center gap-3 text-xs ${className ?? ''}`}>
            <Ship className="size-3 text-blue-400 shrink-0" />
            <LimitSegment label="함대" current={limits.fleets} max={limits.maxFleets} />
            <span className="text-muted-foreground">|</span>
            <LimitSegment label="순찰대" current={limits.patrols} max={limits.maxPatrols} />
            <span className="text-muted-foreground">|</span>
            <LimitSegment label="지상부대" current={limits.groundUnits} max={limits.maxGroundUnits} />
        </div>
    );
}

export function FleetLimitsBadgeCompact({ limits, className }: FleetLimitsBadgeProps) {
    return (
        <Badge variant="outline" className={`font-mono text-xs gap-1 ${className ?? ''}`}>
            <Ship className="size-3" />
            함대 {limits.fleets}/{limits.maxFleets} · 순찰대 {limits.patrols}/{limits.maxPatrols} · 지상부대{' '}
            {limits.groundUnits}/{limits.maxGroundUnits}
        </Badge>
    );
}
