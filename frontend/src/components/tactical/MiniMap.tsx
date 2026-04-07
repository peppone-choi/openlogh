'use client';

import type { TacticalUnit } from '@/types/tactical';

interface MiniMapProps {
    units: TacticalUnit[];
    myOfficerId?: number;
    /** Viewport area as fractions of game coords (0-1) */
    viewport?: { x: number; y: number; w: number; h: number };
}

const MINIMAP_SIZE = 120;
// Game coord range: 0-1000 x, 0-600 y
const GAME_W = 1000;
const GAME_H = 600;

export function MiniMap({ units, myOfficerId, viewport }: MiniMapProps) {
    const aliveUnits = units.filter((u) => u.isAlive);

    return (
        <div
            style={{
                position: 'absolute',
                top: 8,
                right: 8,
                width: MINIMAP_SIZE,
                zIndex: 10,
            }}
        >
            <div
                style={{
                    color: '#ff8800',
                    fontSize: 9,
                    fontFamily: 'monospace',
                    marginBottom: 2,
                    textAlign: 'center',
                    letterSpacing: 1,
                }}
            >
                성계 미니맵
            </div>
            <div
                style={{
                    width: MINIMAP_SIZE,
                    height: MINIMAP_SIZE,
                    background: '#000',
                    border: '1px solid #ff8800',
                    position: 'relative',
                    overflow: 'hidden',
                }}
            >
                {/* Grid lines */}
                {[0.25, 0.5, 0.75].map((f) => (
                    <div key={`hg-${f}`}>
                        <div
                            style={{
                                position: 'absolute',
                                top: f * MINIMAP_SIZE,
                                left: 0,
                                right: 0,
                                height: 1,
                                background: '#331a00',
                            }}
                        />
                        <div
                            style={{
                                position: 'absolute',
                                left: f * MINIMAP_SIZE,
                                top: 0,
                                bottom: 0,
                                width: 1,
                                background: '#331a00',
                            }}
                        />
                    </div>
                ))}

                {/* Unit dots */}
                {aliveUnits.map((unit) => {
                    const dotX = (unit.posX / GAME_W) * MINIMAP_SIZE;
                    // Fit the 600-tall game into the 120-px square minimap (scale by MINIMAP_SIZE/GAME_H)
                    const dotY = (unit.posY / GAME_H) * MINIMAP_SIZE;
                    const isMe = unit.officerId === myOfficerId;
                    const isAlly = unit.side === (aliveUnits.find((u) => u.officerId === myOfficerId)?.side ?? 'ATTACKER');
                    const color = isMe ? '#ffffff' : isAlly ? '#ff8800' : '#884400';

                    return (
                        <div
                            key={unit.fleetId}
                            style={{
                                position: 'absolute',
                                left: dotX - 2,
                                top: dotY - 2,
                                width: isMe ? 5 : 3,
                                height: isMe ? 5 : 3,
                                borderRadius: '50%',
                                background: color,
                            }}
                        />
                    );
                })}

                {/* Viewport indicator */}
                {viewport && (
                    <div
                        style={{
                            position: 'absolute',
                            left: viewport.x * MINIMAP_SIZE,
                            top: viewport.y * MINIMAP_SIZE,
                            width: viewport.w * MINIMAP_SIZE,
                            height: viewport.h * MINIMAP_SIZE,
                            border: '1px solid rgba(255,255,255,0.5)',
                            pointerEvents: 'none',
                        }}
                    />
                )}
            </div>
        </div>
    );
}
