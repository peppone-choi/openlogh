'use client';

import { useState } from 'react';
import { useTutorialStore } from '@/stores/tutorialStore';
import { MOCK_COMMAND_TABLE, getMockCommandResult } from '@/data/tutorial';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

export default function TutorialCommandPage() {
    const { currentStep, steps } = useTutorialStore();
    const step = steps[currentStep];
    const [selectedCommand, setSelectedCommand] = useState<string | null>(null);
    const [result, setResult] = useState<string[] | null>(null);

    const handleExecute = (actionCode: string) => {
        setSelectedCommand(actionCode);
        const commandResult = getMockCommandResult(actionCode);
        setResult(commandResult.logs);
    };

    // Determine which commands to highlight based on current step
    const highlightCommand = (() => {
        switch (step?.id) {
            case 5:
                return 'che_develop_agri';
            case 8:
                return 'che_recruit';
            case 9:
                return 'che_train';
            case 10:
                return 'che_war';
            default:
                return null;
        }
    })();

    return (
        <div className="max-w-2xl mx-auto p-4 space-y-4 pb-20">
            <h1 className="text-xl font-bold">커맨드</h1>

            {/* 커맨드 목록 */}
            <Card data-tutorial="command-panel">
                <CardHeader>
                    <CardTitle className="text-base">행동 선택</CardTitle>
                </CardHeader>
                <CardContent className="space-y-2">
                    {MOCK_COMMAND_TABLE.map((cmd) => {
                        const isHighlighted = cmd.actionCode === highlightCommand;
                        const isSelected = cmd.actionCode === selectedCommand;

                        return (
                            <Button
                                key={cmd.actionCode}
                                variant={isSelected ? 'default' : isHighlighted ? 'secondary' : 'outline'}
                                className={`w-full justify-between h-auto py-3 ${isHighlighted && !isSelected ? 'ring-2 ring-primary animate-pulse' : ''}`}
                                onClick={() => handleExecute(cmd.actionCode)}
                            >
                                <div className="flex items-center gap-2">
                                    <span className="font-medium">{cmd.name}</span>
                                    <Badge variant="outline" className="text-[10px]">
                                        {cmd.category}
                                    </Badge>
                                </div>
                                <span className="text-xs text-muted-foreground">CP: {cmd.commandPointCost}</span>
                            </Button>
                        );
                    })}
                </CardContent>
            </Card>

            {/* 실행 결과 */}
            {result && (
                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">실행 결과</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <div className="space-y-1 text-sm font-mono bg-muted/50 rounded p-3">
                            {result.map((log, i) => (
                                <p key={i} className="text-muted-foreground">
                                    {log}
                                </p>
                            ))}
                        </div>
                    </CardContent>
                </Card>
            )}
        </div>
    );
}
