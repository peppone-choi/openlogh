'use client';

import type { MilitaryUnit, CrewSlotRole } from '@/types';
import { CREW_SLOT_INFO } from '@/types';

interface CrewRosterPanelProps {
    unit: MilitaryUnit;
}

/** Crew slot layouts per unit type */
const CREW_SLOTS: Record<string, CrewSlotRole[]> = {
    FLEET: [
        'COMMANDER', 'VICE_COMMANDER', 'CHIEF_OF_STAFF',
        'STAFF_OFFICER_1', 'STAFF_OFFICER_2', 'STAFF_OFFICER_3',
        'STAFF_OFFICER_4', 'STAFF_OFFICER_5', 'STAFF_OFFICER_6',
        'ADJUTANT',
    ],
    PATROL: ['COMMANDER', 'VICE_COMMANDER', 'ADJUTANT'],
    TRANSPORT: ['COMMANDER', 'VICE_COMMANDER', 'ADJUTANT'],
    GROUND: ['COMMANDER'],
    GARRISON: ['COMMANDER'],
    SOLO: [],
};

export default function CrewRosterPanel({ unit }: CrewRosterPanelProps) {
    const slots = CREW_SLOTS[unit.unitType] ?? [];
    if (slots.length === 0) return null;

    const crewBySlot = new Map(unit.crew.map((c) => [c.slotRole, c]));

    return (
        <div>
            <h4 className="text-sm font-semibold text-gray-300 mb-1">승조원 편성</h4>
            <table className="w-full text-sm">
                <thead>
                    <tr className="text-left text-gray-500 border-b border-gray-700">
                        <th className="py-1 pr-2 font-medium">직책</th>
                        <th className="py-1 font-medium">장교</th>
                    </tr>
                </thead>
                <tbody>
                    {slots.map((slotRole) => {
                        const member = crewBySlot.get(slotRole);
                        const slotInfo = CREW_SLOT_INFO[slotRole];
                        return (
                            <tr key={slotRole} className="border-b border-gray-800">
                                <td className="py-1 pr-2 text-gray-400">{slotInfo.nameKo}</td>
                                <td
                                    className={`py-1 ${member ? 'text-gray-200' : 'text-yellow-500'}`}
                                >
                                    {member ? member.officerName : '공석'}
                                </td>
                            </tr>
                        );
                    })}
                </tbody>
            </table>
        </div>
    );
}
