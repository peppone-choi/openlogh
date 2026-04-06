'use client';

import type { Formation } from '@/types/tactical';
import { FORMATIONS } from '@/types/tactical';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Button } from '@/components/ui/8bit/button';

interface FormationSelectorProps {
    current: Formation;
    onChange: (formation: Formation) => void;
    disabled?: boolean;
}

export function FormationSelector({ current, onChange, disabled = false }: FormationSelectorProps) {
    return (
        <Card>
            <CardHeader className="pb-2">
                <CardTitle className="text-sm">진형 선택</CardTitle>
            </CardHeader>
            <CardContent className="grid grid-cols-2 gap-2">
                {FORMATIONS.map((f) => (
                    <Button
                        key={f.type}
                        variant={current === f.type ? 'default' : 'outline'}
                        size="sm"
                        className="flex flex-col items-start h-auto py-2 px-3"
                        onClick={() => onChange(f.type)}
                        disabled={disabled}
                    >
                        <span className="font-bold text-xs">{f.nameKo}</span>
                        <span className="text-[10px] text-muted-foreground mt-0.5">{f.description}</span>
                    </Button>
                ))}
            </CardContent>
        </Card>
    );
}
