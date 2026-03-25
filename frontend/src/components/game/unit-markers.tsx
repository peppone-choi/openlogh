'use client';

import { getCrewTypeIconUrl } from '@/lib/image';
import type { General } from '@/types';

export interface UnitMarker {
    generalId: number;
    name: string;
    nationColor: string;
    posX: number;
    posY: number;
    crew: number;
    crewType: number;
    isMoving: boolean;
    destX?: number;
    destY?: number;
    isEnemy: boolean;
}

interface UnitMarkersProps {
    markers: UnitMarker[];
    mapScale: number;
    onMarkerClick?: (generalId: number) => void;
}

export function UnitMarkers({ markers, mapScale, onMarkerClick }: UnitMarkersProps) {
    return (
        <>
            {markers.map((marker) => {
                const left = marker.posX * mapScale;
                const top = marker.posY * mapScale;
                const borderColor = marker.isEnemy ? '#ef4444' : marker.nationColor;
                const bgColor = marker.isEnemy ? 'rgba(60,0,0,0.85)' : 'rgba(0,0,0,0.82)';

                return (
                    <div
                        key={marker.generalId}
                        style={{ position: 'absolute', left: 0, top: 0, pointerEvents: 'none' }}
                    >
                        {/* Movement line */}
                        {marker.isMoving && marker.destX != null && marker.destY != null && (
                            <svg
                                style={{
                                    position: 'absolute',
                                    left: 0,
                                    top: 0,
                                    width: '100%',
                                    height: '100%',
                                    overflow: 'visible',
                                    pointerEvents: 'none',
                                }}
                            >
                                <line
                                    x1={marker.posX * mapScale}
                                    y1={marker.posY * mapScale}
                                    x2={marker.destX * mapScale}
                                    y2={marker.destY * mapScale}
                                    stroke={marker.nationColor}
                                    strokeWidth={1.5}
                                    strokeDasharray="4 3"
                                    opacity={0.7}
                                />
                            </svg>
                        )}

                        {/* Marker badge */}
                        <button
                            type="button"
                            style={{
                                position: 'absolute',
                                left,
                                top,
                                transform: 'translate(-50%, -100%)',
                                display: 'flex',
                                alignItems: 'center',
                                gap: 2,
                                padding: '1px 3px',
                                borderRadius: 3,
                                border: `1px solid ${borderColor}`,
                                background: bgColor,
                                color: '#fff',
                                fontSize: 10,
                                lineHeight: '14px',
                                whiteSpace: 'nowrap',
                                cursor: onMarkerClick ? 'pointer' : 'default',
                                pointerEvents: onMarkerClick ? 'auto' : 'none',
                                zIndex: 6,
                            }}
                            onClick={onMarkerClick ? () => onMarkerClick(marker.generalId) : undefined}
                            aria-label={marker.name}
                        >
                            <img
                                src={getCrewTypeIconUrl(marker.crewType)}
                                width={16}
                                height={16}
                                alt=""
                                style={{ display: 'block', flexShrink: 0 }}
                            />
                            <span style={{ maxWidth: 40, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                {marker.name.slice(0, 4)}
                            </span>
                            {!marker.isEnemy && (
                                <span style={{ color: '#aaa', fontSize: 9 }}>{marker.crew.toLocaleString()}</span>
                            )}
                        </button>
                    </div>
                );
            })}
        </>
    );
}

export function buildUnitMarkers(
    generals: General[],
    myNationId: number,
    nationColorMap: Map<number, string>
): UnitMarker[] {
    const result: UnitMarker[] = [];
    for (const g of generals) {
        if (!(g.posX > 0) || !(g.crew > 0)) continue;
        const isMoving = g.destX != null && g.destY != null && (g.destX !== g.posX || g.destY !== g.posY);
        result.push({
            generalId: g.id,
            name: g.name,
            nationColor: nationColorMap.get(g.nationId) ?? '#888',
            posX: g.posX,
            posY: g.posY,
            crew: g.crew,
            crewType: g.crewType,
            isMoving,
            destX: g.destX ?? undefined,
            destY: g.destY ?? undefined,
            isEnemy: g.nationId !== myNationId,
        });
    }
    return result;
}
