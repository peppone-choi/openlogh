import { cn } from '@/lib/utils';

import { Skeleton as ShadcnSkeleton } from '@/components/ui/skeleton';

import '@/components/ui/8bit/styles/retro.css';

export interface BitSkeletonProp extends React.ComponentProps<'div'> {
    asChild?: boolean;
}

function Skeleton({ children, ...props }: BitSkeletonProp) {
    const { className } = props;

    return (
        <div className={cn('relative animate-pulse', className)}>
            <ShadcnSkeleton {...props} className={cn('rounded-none border-none bg-accent', 'retro', className)}>
                {children}
            </ShadcnSkeleton>

            <div className="opacity-60">
                <div className="absolute -top-0.5 w-1/2 left-0.5 h-0.5 bg-foreground/20" />
                <div className="absolute -top-0.5 w-1/2 right-0.5 h-0.5 bg-foreground/20" />
            </div>
            <div className="opacity-60">
                <div className="absolute -bottom-0.5 w-1/2 left-0.5 h-0.5 bg-foreground/20" />
                <div className="absolute -bottom-0.5 w-1/2 right-0.5 h-0.5 bg-foreground/20" />
            </div>
            <div className="absolute top-0 left-0 size-0.5 bg-foreground/12" />
            <div className="absolute top-0 right-0 size-0.5 bg-foreground/12" />
            <div className="absolute bottom-0 left-0 size-0.5 bg-foreground/12" />
            <div className="absolute bottom-0 right-0 size-0.5 bg-foreground/12" />
            <div className="opacity-60">
                <div className="absolute top-0.5 -left-0.5 h-1/2 w-0.5 bg-foreground/20" />
                <div className="absolute bottom-0.5 -left-0.5 h-1/2 w-0.5 bg-foreground/20" />
            </div>
            <div className="opacity-60">
                <div className="absolute top-0.5 -right-0.5 h-1/2 w-0.5 bg-foreground/20" />
                <div className="absolute bottom-0.5 -right-0.5 h-1/2 w-0.5 bg-foreground/20" />
            </div>
        </div>
    );
}

export { Skeleton };
