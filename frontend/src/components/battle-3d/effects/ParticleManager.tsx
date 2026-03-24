'use client';

import { useMemo } from 'react';
import { AttackEffect } from './AttackEffect';
import { ImpactEffect } from './ImpactEffect';
import type { PhaseEvent } from '@/types/battle3d';

interface ParticleManagerProps {
    events: PhaseEvent[];
    attackerPosition: [number, number, number];
    defenderPosition: [number, number, number];
    active: boolean;
}

type AttackType = 'normal' | 'critical' | 'fire' | 'ice' | 'lightning';
type ImpactType = 'hit' | 'dust' | 'spark';

interface EffectSpec {
    id: string;
    attackType: AttackType | null;
    impactType: ImpactType | null;
}

function mapEventsToEffects(events: PhaseEvent[]): EffectSpec[] {
    // If no events, emit one default normal attack
    if (events.length === 0) {
        return [{ id: 'default', attackType: 'normal', impactType: 'hit' }];
    }

    return events.map((event, i): EffectSpec => {
        const id = `${event}-${i}`;
        switch (event) {
            case 'critical':
                return { id, attackType: 'critical', impactType: 'spark' };
            case 'dodge':
                // Miss — no attack or impact
                return { id, attackType: null, impactType: 'dust' };
            case 'trigger_fire':
                return { id, attackType: 'fire', impactType: 'spark' };
            case 'trigger_ice':
                return { id, attackType: 'ice', impactType: 'hit' };
            case 'trigger_lightning':
                return { id, attackType: 'lightning', impactType: 'spark' };
            default:
                return { id, attackType: 'normal', impactType: 'hit' };
        }
    });
}

export function ParticleManager({ events, attackerPosition, defenderPosition, active }: ParticleManagerProps) {
    const specs = useMemo(() => mapEventsToEffects(events), [events]);

    return (
        <>
            {specs.map((spec) => (
                <group key={spec.id}>
                    {spec.attackType !== null && (
                        <AttackEffect
                            from={attackerPosition}
                            to={defenderPosition}
                            type={spec.attackType}
                            active={active}
                        />
                    )}
                    {spec.impactType !== null && (
                        <ImpactEffect position={defenderPosition} type={spec.impactType} active={active} />
                    )}
                </group>
            ))}
        </>
    );
}
