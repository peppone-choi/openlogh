'use client';
// BattleCloseViewPanel — left or right officer info panel for battle close-range view
import type { TacticalUnit } from '@/types/tactical';

const SHIP_CLASS_NAMES: Record<string, string> = {
    battleship: '전함',
    cruiser: '순양함',
    destroyer: '구축함',
    carrier: '항공모함',
    transport: '수송함',
    hospital: '병원선',
    fortress: '요새',
};

interface OfficerInfo {
    name: string;
    rankTitle: string;
    factionName: string;
    factionColor: string;
}

interface UnitInfo {
    fleetNumber: number;
    unitNumber: number;
    shipClass: string;
    ships: number;
    shipsMax: number;
    morale: number;
    weaponName: string;
}

interface BattleCloseViewPanelProps {
    side: 'ally' | 'enemy';
    officer: OfficerInfo;
    unit: UnitInfo;
}

export function BattleCloseViewPanel({ side, officer, unit }: BattleCloseViewPanelProps) {
    const bgColor = side === 'ally' ? '#3a1010' : '#0d1a3a';
    const moralePercent = Math.max(0, Math.min(100, unit.morale));
    const shipClassName = SHIP_CLASS_NAMES[unit.shipClass] ?? unit.shipClass;

    return (
        <div
            style={{
                width: '180px',
                flexShrink: 0,
                backgroundColor: bgColor,
                borderRight: side === 'ally' ? `1px solid ${officer.factionColor}40` : undefined,
                borderLeft: side === 'enemy' ? `1px solid ${officer.factionColor}40` : undefined,
                padding: '12px',
                display: 'flex',
                flexDirection: 'column',
                gap: '8px',
                overflowY: 'auto',
                fontSize: '12px',
            }}
        >
            {/* Officer portrait placeholder */}
            <div
                style={{
                    width: '60px',
                    height: '60px',
                    border: `2px solid ${officer.factionColor}`,
                    backgroundColor: '#333',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    margin: '0 auto',
                    color: '#999',
                    fontSize: '10px',
                    flexShrink: 0,
                }}
            >
                초상화
            </div>

            {/* Faction name */}
            <div
                style={{
                    color: officer.factionColor,
                    fontWeight: 'bold',
                    textAlign: 'center',
                    fontSize: '11px',
                }}
            >
                {officer.factionName}
            </div>

            {/* Officer name */}
            <div
                style={{
                    color: 'white',
                    fontWeight: 'bold',
                    textAlign: 'center',
                    fontSize: '13px',
                }}
            >
                {officer.name}
            </div>

            {/* Rank */}
            <div style={{ color: '#aaa', textAlign: 'center', fontSize: '10px' }}>
                {officer.rankTitle}
            </div>

            {/* Fleet/unit number */}
            <div style={{ color: '#888', fontSize: '10px', textAlign: 'center' }}>
                함대{unit.fleetNumber}번 {unit.unitNumber}번 부대
            </div>

            {/* Ship class + count */}
            <div style={{ borderTop: '1px solid #333', paddingTop: '6px' }}>
                <div style={{ color: '#bbb', marginBottom: '3px' }}>
                    {shipClassName}
                </div>
                <div style={{ color: 'white', fontWeight: 'bold' }}>
                    {unit.ships.toLocaleString()}/{unit.shipsMax.toLocaleString()}
                </div>
            </div>

            {/* Morale bar */}
            <div>
                <div style={{ color: '#888', marginBottom: '3px', fontSize: '10px' }}>
                    함대사기
                </div>
                <div
                    style={{
                        width: '100%',
                        height: '6px',
                        backgroundColor: '#222',
                        borderRadius: '3px',
                        overflow: 'hidden',
                    }}
                >
                    <div
                        style={{
                            width: `${moralePercent}%`,
                            height: '100%',
                            backgroundColor: officer.factionColor,
                            transition: 'width 0.3s ease',
                        }}
                    />
                </div>
                <div style={{ color: '#888', fontSize: '10px', marginTop: '2px', textAlign: 'right' }}>
                    {moralePercent}
                </div>
            </div>

            {/* Weapon name */}
            <div>
                <div style={{ color: '#888', fontSize: '10px', marginBottom: '2px' }}>
                    사용무기
                </div>
                <div style={{ color: '#ddd', fontSize: '11px' }}>
                    {unit.weaponName || '-'}
                </div>
            </div>
        </div>
    );
}

// Helper to build OfficerInfo and UnitInfo from TacticalUnit for a given faction
export function buildPanelProps(unit: TacticalUnit | undefined, factionColor: string, factionName: string): {
    officer: OfficerInfo;
    unit: UnitInfo;
} | null {
    if (!unit) return null;
    return {
        officer: {
            name: unit.officerName,
            rankTitle: '',
            factionName,
            factionColor,
        },
        unit: {
            fleetNumber: unit.fleetId,
            unitNumber: unit.fleetId,
            shipClass: unit.unitType?.toLowerCase() ?? 'battleship',
            ships: unit.ships,
            shipsMax: unit.maxShips,
            morale: unit.morale,
            weaponName: '',
        },
    };
}
