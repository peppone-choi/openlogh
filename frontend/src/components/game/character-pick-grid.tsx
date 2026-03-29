'use client';

import type { Officer } from '@/types';
import { STAT_KEYS_8, STAT_LABELS_KO, STAT_COLORS } from '@/types';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { OfficerPortrait } from '@/components/game/officer-portrait';
import { StatBar } from '@/components/game/stat-bar';

interface CharacterPickGridProps {
    characters: Officer[];
    onSelect: (officerId: number) => void;
    selecting: number | null;
}

export function CharacterPickGrid({ characters, onSelect, selecting }: CharacterPickGridProps) {
    return (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {characters.map((officer) => (
                <Card key={officer.id}>
                    <CardContent className="space-y-3">
                        <div className="flex items-center gap-3">
                            <OfficerPortrait
                                picture={officer.picture}
                                name={officer.name}
                                size="md"
                            />
                            <div>
                                <p className="text-sm font-semibold">{officer.name}</p>
                                <Badge variant="outline" className="mt-1">
                                    {officer.age}세
                                </Badge>
                            </div>
                        </div>
                        <div className="space-y-1">
                            {STAT_KEYS_8.map((key) => (
                                <StatBar
                                    key={key}
                                    label={STAT_LABELS_KO[key]}
                                    value={officer[key]}
                                    max={100}
                                    color={STAT_COLORS[key]}
                                />
                            ))}
                        </div>
                        <Button
                            className="w-full"
                            onClick={() => onSelect(officer.id)}
                            disabled={selecting !== null}
                        >
                            {selecting === officer.id ? '선택 중...' : '제독 선택하기'}
                        </Button>
                    </CardContent>
                </Card>
            ))}
        </div>
    );
}
