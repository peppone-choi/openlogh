// Phase 14 Plan 14-12/14-13 — Draggable sub-fleet unit chip (FE-02, D-05).
//
// NOTE (14-13 stub handoff): This file was created in parallel Wave 4 to unblock
// the drawer component. 14-12 is the canonical owner for its visual polish; the
// contract below is locked so 14-13's drawer can import it today.
//
// Contract:
//   - Draggable via @dnd-kit/core useDraggable, id = `unit-{fleetId}`.
//   - `disabled` blocks drag start (dnd-kit native disabled flag).
//   - `disabledReason` renders a Radix Tooltip with Korean gating copy.
//   - Faction-colored 3px left border via sideToDefaultColor(side).
//   - 28px height, --accent bg, opacity 0.4 when disabled.
//   - cursor: grab (enabled) / not-allowed (disabled).
//   - aria-disabled reflects disabled state for a11y.

'use client';

import { useDraggable } from '@dnd-kit/core';
import { CSS } from '@dnd-kit/utilities';
import type { TacticalUnit } from '@/types/tactical';
import { sideToDefaultColor } from '@/lib/tacticalColors';
import {
    Tooltip,
    TooltipContent,
    TooltipProvider,
    TooltipTrigger,
} from '@/components/ui/tooltip';

export interface SubFleetUnitChipProps {
    unit: TacticalUnit;
    disabled?: boolean;
    disabledReason?: string;
}

export function SubFleetUnitChip({
    unit,
    disabled = false,
    disabledReason,
}: SubFleetUnitChipProps) {
    const { attributes, listeners, setNodeRef, transform } = useDraggable({
        id: `unit-${unit.fleetId}`,
        disabled,
        data: {
            fleetId: unit.fleetId,
            officerId: unit.officerId,
        },
    });

    const accentColor = sideToDefaultColor(unit.side);

    const style: React.CSSProperties = {
        transform: CSS.Translate.toString(transform) ?? undefined,
        height: 28,
        borderLeft: `3px solid ${accentColor}`,
        backgroundColor: 'var(--accent)',
        opacity: disabled ? 0.4 : 1,
        cursor: disabled ? 'not-allowed' : 'grab',
        display: 'flex',
        alignItems: 'center',
        paddingLeft: 8,
        paddingRight: 8,
        gap: 8,
        fontSize: 12,
        lineHeight: 1.4,
        borderRadius: 2,
        userSelect: 'none',
        touchAction: 'none',
    };

    // Spread {...attributes} FIRST so our explicit aria-disabled override
    // wins — @dnd-kit's useDraggable also sets aria-disabled in `attributes`
    // when `disabled=true`, and TypeScript treats the duplicate literal as a
    // TS2783 error. The spread-then-override order is both type-safe and
    // semantically correct (we want our prop to be the source of truth).
    const body = (
        <div
            ref={setNodeRef}
            style={style}
            data-testid={`sub-fleet-unit-chip-${unit.fleetId}`}
            {...(disabled ? {} : listeners)}
            {...attributes}
            aria-disabled={disabled}
        >
            <span className="truncate">{unit.officerName}</span>
            <span className="ml-auto font-mono tabular-nums text-muted-foreground">
                {unit.ships}
            </span>
        </div>
    );

    if (disabled && disabledReason) {
        return (
            <TooltipProvider delayDuration={400}>
                <Tooltip>
                    <TooltipTrigger asChild>{body}</TooltipTrigger>
                    <TooltipContent>{disabledReason}</TooltipContent>
                </Tooltip>
            </TooltipProvider>
        );
    }

    return body;
}
