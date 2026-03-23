import type { Metadata } from 'next';
import { ThemeProvider } from 'next-themes';
import { Toaster } from 'sonner';
import './globals.css';

export const metadata: Metadata = {
    title: '오픈 은하영웅전설',
    description: '은하영웅전설 세계관 멀티플레이어 전략 시뮬레이션 게임',
};

export default function RootLayout({
    children,
}: Readonly<{
    children: React.ReactNode;
}>) {
    return (
        <html lang="ko" suppressHydrationWarning>
            <head>
                <link
                    rel="stylesheet"
                    href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard/dist/web/static/pretendard.css"
                />
            </head>
            <body className="antialiased legacy-ui">
                <ThemeProvider attribute="class" defaultTheme="dark" enableSystem={false}>
                    {children}
                    <Toaster richColors position="top-right" />
                </ThemeProvider>
            </body>
        </html>
    );
}
