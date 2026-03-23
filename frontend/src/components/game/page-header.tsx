'use client';

import { usePathname, useRouter } from 'next/navigation';
import type { LucideIcon } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface PageHeaderProps {
    icon?: LucideIcon;
    title: string;
    description?: string;
}

export function PageHeader({ icon: Icon, title, description }: PageHeaderProps) {
    const router = useRouter();
    const pathname = usePathname();

    const isMainPage = pathname === '/';

    return (
        <div className="space-y-1 legacy-page-wrap">
            <div
                style={{
                    display: 'grid',
                    gridTemplateColumns: '90px 90px 1fr 90px 90px',
                    minHeight: '32px',
                    borderTop: '1px solid rgba(201,168,76,0.35)',
                    borderBottom: '1px solid rgba(201,168,76,0.35)',
                    background: 'linear-gradient(180deg, rgba(201,168,76,0.06) 0%, rgba(0,0,0,0) 100%)',
                    alignItems: 'stretch',
                }}
            >
                <Button
                    variant="ghost"
                    size="sm"
                    className="h-full rounded-none border-r font-mono text-xs tracking-wide"
                    style={{ borderColor: 'rgba(201,168,76,0.25)', color: 'rgba(201,168,76,0.7)' }}
                    onClick={() => {
                        if (isMainPage) {
                            router.push('/lobby');
                            return;
                        }
                        router.push('/');
                    }}
                >
                    {isMainPage ? '로비로' : '돌아가기'}
                </Button>
                <Button
                    variant="ghost"
                    size="sm"
                    className="h-full rounded-none border-r font-mono text-xs tracking-wide"
                    style={{ borderColor: 'rgba(201,168,76,0.25)', color: 'rgba(201,168,76,0.7)' }}
                    onClick={() => window.location.reload()}
                >
                    갱신
                </Button>
                <h2
                    className="m-0 flex items-center justify-center gap-1.5 text-sm font-bold tracking-widest font-mono"
                    style={{ color: 'var(--empire-gold, #c9a84c)' }}
                >
                    {Icon && <Icon className="h-3.5 w-3.5 shrink-0" style={{ color: 'var(--empire-gold, #c9a84c)' }} />}
                    {title}
                </h2>
                <div style={{ borderLeft: '1px solid rgba(201,168,76,0.25)' }} />
                <div style={{ borderLeft: '1px solid rgba(201,168,76,0.25)' }} />
            </div>
            {description && (
                <p
                    className="px-2 py-1 text-xs"
                    style={{
                        border: '1px solid rgba(201,168,76,0.2)',
                        background: 'rgba(201,168,76,0.04)',
                        color: 'rgba(201,168,76,0.6)',
                        fontFamily: 'monospace',
                    }}
                >
                    {description}
                </p>
            )}
        </div>
    );
}
