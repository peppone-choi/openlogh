'use client';

import { Group, RegularPolygon, Text } from 'react-konva';
import type { FleetPosition } from '@/types/galaxy';
import { FACTION_SHADES } from '@/types/galaxy';

interface FleetPositionMarkerProps {
    /** Canvas X coordinate of the parent star system center */
    x: number;
    /** Canvas Y coordinate of the parent star system center */
    y: number;
    /** Fleet data to render */
    fleet: FleetPosition;
    /** Index for horizontal stacking when multiple fleets are at same system */
    index: number;
    /** Whether this fleet is selected */
    isSelected?: boolean;
    /** Callback when this marker is clicked */
    onClick?: (fleetId: number) => void;
}

/** Resolve a bright faction color for fleet markers (use shade index 4 = brightest) */
function getFleetColor(factionId: number): string {
    if (factionId === 1) return FACTION_SHADES.empire[4];
    if (factionId === 2) return FACTION_SHADES.alliance[4];
    if (factionId === 3) return FACTION_SHADES.fezzan[4];
    if (factionId === 4) return FACTION_SHADES.rebel[4];
    return FACTION_SHADES.neutral[4];
}

/**
 * Small triangle marker (gin7 △ style) shown above a star system node.
 * Multiple fleets stack horizontally: offset = index * 10 - 5
 */
export function FleetPositionMarker({
    x,
    y,
    fleet,
    index,
    isSelected = false,
    onClick,
}: FleetPositionMarkerProps) {
    const color = getFleetColor(fleet.factionId);
    // Stack horizontally: -5, +5, +15, ...
    const markerX = x - 5 + index * 10;
    const markerY = y - 16;

    return (
        <Group
            x={markerX}
            y={markerY}
            onClick={() => onClick?.(fleet.fleetId)}
            onTap={() => onClick?.(fleet.fleetId)}
        >
            {/* Glow behind triangle when selected */}
            {isSelected && (
                <RegularPolygon
                    sides={3}
                    radius={8}
                    fill={color}
                    opacity={0.3}
                    listening={false}
                    perfectDrawEnabled={false}
                />
            )}

            {/* Triangle marker */}
            <RegularPolygon
                sides={3}
                radius={5}
                fill={color}
                stroke={isSelected ? '#ffffff' : color}
                strokeWidth={isSelected ? 1.5 : 0.5}
                opacity={0.9}
                perfectDrawEnabled={false}
            />

            {/* Ship count label (only if > 0) */}
            {fleet.ships > 0 && (
                <Text
                    text={fleet.ships >= 1000 ? `${Math.floor(fleet.ships / 1000)}k` : String(fleet.ships)}
                    fontSize={7}
                    fontFamily="'DungGeunMo', monospace"
                    fill="#cccccc"
                    align="center"
                    x={-8}
                    y={7}
                    width={16}
                    listening={false}
                />
            )}
        </Group>
    );
}
