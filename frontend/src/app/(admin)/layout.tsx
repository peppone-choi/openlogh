'use client';

import { useEffect } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import Link from 'next/link';
import { useAuthStore } from '@/stores/authStore';
import { AdminWorldProvider, useAdminWorld } from '@/contexts/AdminWorldContext';
import { Toaster } from 'sonner';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
    LayoutDashboard,
    Users,
    BarChart3,
    ScrollText,
    Handshake,
    Clock,
    UserCog,
    Container,
    LogOut,
    Globe,
} from 'lucide-react';

const adminNav = [
    { href: '/admin', label: '대시보드', icon: LayoutDashboard },
    { href: '/admin/members', label: '장수 관리', icon: Users },
    { href: '/admin/statistics', label: '통계', icon: BarChart3 },
    { href: '/admin/logs', label: '로그', icon: ScrollText },
    { href: '/admin/diplomacy', label: '외교', icon: Handshake },
    { href: '/admin/select-pool', label: '장수 풀', icon: Users },
    { href: '/admin/time-control', label: '시간 제어', icon: Clock },
    { href: '/admin/users', label: '유저 관리', icon: UserCog },
    { href: '/admin/game-versions', label: '게임 버전', icon: Container },
];

function WorldSelector() {
    const { worlds, worldId, setWorldId, loading } = useAdminWorld();

    if (loading || worlds.length === 0) return null;

    return (
        <div className="flex items-center gap-2">
            <Globe className="size-4 text-muted-foreground" />
            <Select value={worldId != null ? String(worldId) : undefined} onValueChange={(v) => setWorldId(Number(v))}>
                <SelectTrigger size="sm" className="w-[200px]">
                    <SelectValue placeholder="월드 선택" />
                </SelectTrigger>
                <SelectContent>
                    {worlds.map((w) => (
                        <SelectItem key={w.id} value={String(w.id)}>
                            #{w.id} {w.scenarioCode} ({w.year}년 {w.month}월)
                            {w.locked ? ' [잠금]' : ''}
                        </SelectItem>
                    ))}
                </SelectContent>
            </Select>
        </div>
    );
}

export default function AdminLayout({ children }: { children: React.ReactNode }) {
    const router = useRouter();
    const pathname = usePathname();
    const { user, isAuthenticated, initAuth, logout } = useAuthStore();
    const isAdmin = isAuthenticated && user?.role === 'ADMIN';

    useEffect(() => {
        initAuth();
    }, [initAuth]);
    useEffect(() => {
        if (!isAuthenticated) {
            router.replace('/login');
        } else if (!isAdmin) {
            router.replace('/');
        }
    }, [isAuthenticated, isAdmin, router]);

    if (!isAdmin) return null;

    return (
        <AdminWorldProvider>
            <div className="flex h-screen bg-background text-foreground">
                <aside className="w-48 border-r border-border bg-card">
                    <div className="h-14 px-4 flex items-center border-b border-border">
                        <Link href="/admin" className="text-lg font-bold text-red-400">
                            관리자
                        </Link>
                    </div>
                    <nav className="p-2 space-y-0.5">
                        {adminNav.map((item) => {
                            const Icon = item.icon;
                            const active = pathname === item.href;
                            return (
                                <Button
                                    key={item.href}
                                    variant="ghost"
                                    size="sm"
                                    asChild
                                    className={`w-full justify-start gap-2 ${active ? 'bg-red-400/10 text-red-400' : 'text-muted-foreground'}`}
                                >
                                    <Link href={item.href}>
                                        <Icon className="size-4" />
                                        {item.label}
                                    </Link>
                                </Button>
                            );
                        })}
                        <Button
                            variant="ghost"
                            size="sm"
                            asChild
                            className="w-full justify-start gap-2 text-muted-foreground"
                        >
                            <Link href="/lobby">
                                <LogOut className="size-4" />
                                로비로
                            </Link>
                        </Button>
                    </nav>
                </aside>
                <div className="flex-1 flex flex-col min-w-0">
                    <header className="h-14 px-4 flex items-center gap-4 border-b border-border bg-card">
                        <span className="text-sm text-muted-foreground">관리자 패널</span>
                        <WorldSelector />
                        <Button
                            variant="ghost"
                            size="sm"
                            className="ml-auto text-muted-foreground"
                            onClick={() => {
                                logout();
                                router.replace('/login');
                            }}
                        >
                            <LogOut className="size-4" />
                        </Button>
                    </header>
                    <main className="flex-1 overflow-y-auto p-4">{children}</main>
                </div>
                <Toaster position="top-right" theme="dark" />
            </div>
        </AdminWorldProvider>
    );
}
