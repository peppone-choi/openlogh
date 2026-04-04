'use client';

import { toast as sonnerToast } from 'sonner';

import '@/components/ui/8bit/styles/retro.css';

export function toast(toast: string) {
    return sonnerToast.custom((id) => <Toast id={id} title={toast} />);
}

interface ToastProps {
    id: string | number;
    title: string;
}

function Toast(props: ToastProps) {
    const { title } = props;

    return (
        <div className={`relative ${'retro'}`}>
            <div className="flex rounded-lg bg-background shadow-lg ring-1 ring-black/5 w-full md:max-w-[364px] items-center p-4">
                <div className="flex flex-1 items-center">
                    <div className="w-full">
                        <p className="text-sm font-medium">{title}</p>
                    </div>
                </div>
            </div>

            <div className="absolute -top-0.5 w-1/2 left-0.5 h-0.5 bg-foreground/20" />
            <div className="absolute -top-0.5 w-1/2 right-0.5 h-0.5 bg-foreground/20" />
            <div className="absolute -bottom-0.5 w-1/2 left-0.5 h-0.5 bg-foreground/20" />
            <div className="absolute -bottom-0.5 w-1/2 right-0.5 h-0.5 bg-foreground/20" />
            <div className="absolute top-0 left-0 size-0.5 bg-foreground/20" />
            <div className="absolute top-0 right-0 size-0.5 bg-foreground/20" />
            <div className="absolute bottom-0 left-0 size-0.5 bg-foreground/20" />
            <div className="absolute bottom-0 right-0 size-0.5 bg-foreground/20" />
            <div className="absolute top-0.5 -left-0.5 h-1/2 w-0.5 bg-foreground/20" />
            <div className="absolute bottom-0.5 -left-0.5 h-1/2 w-0.5 bg-foreground/20" />
            <div className="absolute top-0.5 -right-0.5 h-1/2 w-0.5 bg-foreground/20" />
            <div className="absolute bottom-0.5 -right-0.5 h-1/2 w-0.5 bg-foreground/20" />
        </div>
    );
}
