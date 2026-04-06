'use client';

import type { StarSystem } from '@/types/galaxy';
import {
    getFactionColor,
    isFortress,
    FORTRESS_NAMES,
} from '@/types/galaxy';
import { useGalaxyStore } from '@/stores/galaxyStore';

interface StarSystemDetailPanelProps {
    system: StarSystem | null;
    onClose: () => void;
}

function getFactionLabel(system: StarSystem): string {
    if (system.factionName) return system.factionName;
    return '공백지';
}

export function StarSystemDetailPanel({
    system,
    onClose,
}: StarSystemDetailPanelProps) {
    const selectSystem = useGalaxyStore((s) => s.selectSystem);
    const getConnectedSystems = useGalaxyStore((s) => s.getConnectedSystems);

    if (!system) return null;

    const color = system.factionColor || '#444444';
    const hasFortress = isFortress(system);
    const connectedSystems = getConnectedSystems(system.mapStarId);

    return (
        <div
            className="absolute right-4 top-4 z-10 w-72 rounded-lg border border-gray-700/60 bg-[#0d1117]/95 p-4 shadow-2xl backdrop-blur-md"
            style={{
                borderLeftWidth: 3,
                borderLeftColor: hasFortress ? '#ffaa00' : color,
            }}
        >
            {/* Header */}
            <div className="mb-3 flex items-start justify-between">
                <div>
                    <h3 className="text-base font-bold text-white">
                        {system.nameKo}
                    </h3>
                    <p className="text-xs text-gray-400">{system.nameEn}</p>
                </div>
                <button
                    type="button"
                    onClick={onClose}
                    className="rounded p-1 text-gray-500 hover:bg-gray-800 hover:text-white"
                    aria-label="닫기"
                >
                    <svg
                        width="16"
                        height="16"
                        viewBox="0 0 16 16"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                    >
                        <path d="M4 4l8 8M12 4l-8 8" />
                    </svg>
                </button>
            </div>

            {/* Faction badge */}
            <div className="mb-3 flex items-center gap-2">
                <span
                    className="inline-block h-3 w-3 rounded-full"
                    style={{
                        backgroundColor: color,
                        boxShadow: `0 0 6px ${color}80`,
                    }}
                />
                <span className="text-sm text-gray-300">
                    {getFactionLabel(system)}
                </span>
            </div>

            {/* Info grid */}
            <div className="mb-3 grid grid-cols-2 gap-2 text-xs">
                <div className="rounded bg-gray-800/70 px-2 py-1.5">
                    <span className="text-gray-500">항성 분류</span>
                    <p className="font-medium text-gray-200">
                        {system.spectralType}형
                    </p>
                </div>
                <div className="rounded bg-gray-800/70 px-2 py-1.5">
                    <span className="text-gray-500">레벨</span>
                    <p className="font-medium text-gray-200">{system.level}</p>
                </div>
                <div className="rounded bg-gray-800/70 px-2 py-1.5">
                    <span className="text-gray-500">행성 수</span>
                    <p className="font-medium text-gray-200">
                        {system.planetCount}
                    </p>
                </div>
                <div className="rounded bg-gray-800/70 px-2 py-1.5">
                    <span className="text-gray-500">항로 연결</span>
                    <p className="font-medium text-gray-200">
                        {system.connections.length}
                    </p>
                </div>
            </div>

            {/* Fortress section */}
            {hasFortress && (
                <div
                    className="mb-3 rounded border px-3 py-2"
                    style={{ borderColor: '#ffaa0060', backgroundColor: '#ffaa0008' }}
                >
                    <p className="mb-1 text-xs font-bold" style={{ color: '#ffaa00' }}>
                        {FORTRESS_NAMES[system.fortressType]}
                    </p>
                    <div className="grid grid-cols-2 gap-1 text-xs text-gray-300">
                        <span>
                            포대 위력:{' '}
                            <strong className="text-white">
                                {system.fortressGunPower}
                            </strong>
                        </span>
                        <span>
                            사정거리:{' '}
                            <strong className="text-white">
                                {system.fortressGunRange}
                            </strong>
                        </span>
                        <span className="col-span-2">
                            주둔 용량:{' '}
                            <strong className="text-white">
                                {system.garrisonCapacity}
                            </strong>
                        </span>
                    </div>
                </div>
            )}

            {/* Planets in this system */}
            {system.planets && system.planets.length > 0 && (
                <div className="mb-3">
                    <p className="mb-1.5 text-xs font-medium text-gray-400">
                        소속 행성
                    </p>
                    <div className="space-y-1">
                        {system.planets.map((planetName) => (
                            <div
                                key={planetName}
                                className="flex items-center gap-2 rounded bg-gray-800/70 px-2 py-1.5 text-xs"
                            >
                                <span
                                    className="inline-block h-2 w-2 rounded-full"
                                    style={{
                                        backgroundColor: color,
                                        boxShadow: `0 0 4px ${color}60`,
                                    }}
                                />
                                <span className="font-medium text-gray-200">
                                    {planetName}
                                </span>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* Connected systems */}
            {connectedSystems.length > 0 && (
                <div>
                    <p className="mb-1.5 text-xs font-medium text-gray-400">
                        연결된 항성계
                    </p>
                    <div className="flex flex-wrap gap-1">
                        {connectedSystems.map((conn) => (
                            <button
                                key={conn.mapStarId}
                                type="button"
                                onClick={() => selectSystem(conn.mapStarId)}
                                className="rounded bg-gray-800/70 px-2 py-0.5 text-xs text-gray-300 transition-colors hover:bg-gray-700 hover:text-white"
                            >
                                <span
                                    className="mr-1 inline-block h-2 w-2 rounded-full"
                                    style={{
                                        backgroundColor: conn.factionColor || '#444444',
                                    }}
                                />
                                {conn.nameKo}
                            </button>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}
