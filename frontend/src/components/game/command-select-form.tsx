'use client';

import { useState } from 'react';
import { Card, CardContent } from '@/components/ui/8bit/card';
import { Badge } from '@/components/ui/8bit/badge';
import { Button } from '@/components/ui/8bit/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/8bit/tabs';
import { CommandArgForm, COMMAND_ARGS } from '@/components/game/command-arg-form';
import type { CommandArg, CommandTableEntry } from '@/types';

interface CommandSelectFormProps {
    commandTable: Record<string, CommandTableEntry[]>;
    onSelect: (actionCode: string, arg?: CommandArg) => void;
    onCancel: () => void;
    realtimeMode: boolean;
    generalId: number;
}

export function CommandSelectForm({
    commandTable,
    onSelect,
    onCancel,
    realtimeMode,
    generalId,
}: CommandSelectFormProps) {
    const [selectedCmd, setSelectedCmd] = useState('');
    const categories = Object.keys(commandTable);
    const hasArgForm = !!(selectedCmd && COMMAND_ARGS[selectedCmd]);

    const handleArgSubmit = (arg: CommandArg) => {
        if (!selectedCmd) return;
        onSelect(selectedCmd, arg);
    };

    const handleSelectCmd = (actionCode: string) => {
        if (!COMMAND_ARGS[actionCode]) {
            onSelect(actionCode);
            return;
        }
        setSelectedCmd(actionCode);
    };

    return (
        <Card className="border-amber-400/30">
            <CardContent className="space-y-3 pt-3">
                <Tabs defaultValue={categories[0] ?? ''}>
                    <TabsList className="flex-wrap h-auto">
                        {categories.map((cat) => (
                            <TabsTrigger key={cat} value={cat} className="text-xs">
                                {cat}
                            </TabsTrigger>
                        ))}
                    </TabsList>
                    {categories.map((cat) => (
                        <TabsContent key={cat} value={cat}>
                            <div className="flex flex-wrap gap-1">
                                {commandTable[cat]
                                    .filter((cmd) => !cmd.actionCode.startsWith('NPC'))
                                    .map((cmd) => {
                                        const needsArgs = !!COMMAND_ARGS[cmd.actionCode];
                                        return (
                                            <Badge
                                                key={cmd.actionCode}
                                                variant={selectedCmd === cmd.actionCode ? 'default' : 'secondary'}
                                                className={`cursor-pointer text-xs ${
                                                    !cmd.enabled ? 'border border-red-500/50 text-red-400' : ''
                                                }`}
                                                onClick={() => handleSelectCmd(cmd.actionCode)}
                                                title={
                                                    cmd.reason ??
                                                    (needsArgs ? '클릭하여 세부 설정' : '클릭하여 즉시 예약')
                                                }
                                            >
                                                {cmd.name}
                                                {needsArgs && <span className="ml-0.5 text-amber-300">⚙</span>}
                                                {realtimeMode && (
                                                    <span className="ml-1 text-[10px] text-gray-300">
                                                        ({cmd.commandPointCost}CP/{cmd.durationSeconds}s)
                                                        {cmd.poolType && (
                                                            <span className={cmd.poolType === 'MCP' ? 'ml-0.5 text-red-400' : 'ml-0.5 text-blue-400'}>
                                                                [{cmd.poolType}]
                                                            </span>
                                                        )}
                                                    </span>
                                                )}
                                            </Badge>
                                        );
                                    })}
                            </div>
                        </TabsContent>
                    ))}
                </Tabs>

                {selectedCmd && hasArgForm && (
                    <>
                        <CommandArgForm actionCode={selectedCmd} onSubmit={handleArgSubmit} />
                        <div className="flex gap-2">
                            <Button size="sm" variant="ghost" onClick={onCancel}>
                                취소
                            </Button>
                        </div>
                    </>
                )}
            </CardContent>
        </Card>
    );
}
