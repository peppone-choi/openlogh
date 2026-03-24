'use client';

import { ScrollArea } from '@/components/ui/scroll-area';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatLog } from '@/lib/formatLog';
import type { RecordEntry } from '@/types';

interface RecordZoneProps {
    generalRecords: RecordEntry[];
    globalRecords: RecordEntry[];
    historyRecords: RecordEntry[];
}

export function RecordZone({ generalRecords, globalRecords, historyRecords }: RecordZoneProps) {
    return (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <RecordColumn title="제독동향" records={globalRecords} />
            <RecordColumn title="개인기록" records={generalRecords} />
            <RecordColumn title="은하 정세" records={historyRecords} stripYear />
        </div>
    );
}

function RecordColumn({ title, records, stripYear }: { title: string; records: RecordEntry[]; stripYear?: boolean }) {
    return (
        <Card>
            <CardHeader className="pb-2">
                <CardTitle className="text-xs">{title}</CardTitle>
            </CardHeader>
            <CardContent>
                <ScrollArea className="h-40">
                    <div className="space-y-1">
                        {records.length === 0 ? (
                            <p className="text-xs text-muted-foreground">기록 없음</p>
                        ) : (
                            records.map((r) => {
                                const message = stripYear ? r.message.replace(/\d+년\s+\d+월:/g, '') : r.message;
                                const date = stripYear ? r.date.replace(/\d+년\s*/, '') : r.date;
                                return (
                                    <div key={r.id} className="text-xs leading-relaxed">
                                        {date && <span className="text-muted-foreground mr-1">[{date}]</span>}
                                        {formatLog(message)}
                                    </div>
                                );
                            })
                        )}
                    </div>
                </ScrollArea>
            </CardContent>
        </Card>
    );
}
