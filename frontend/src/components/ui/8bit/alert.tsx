import { type VariantProps, cva } from 'class-variance-authority';

import { cn } from '@/lib/utils';

import {
    Alert as ShadcnAlert,
    AlertDescription as ShadcnAlertDescription,
    AlertTitle as ShadcnAlertTitle,
} from '@/components/ui/alert';

export const alertVariants = cva('', {
    variants: {
        font: {
            normal: '',
            retro: 'retro',
        },
        variant: {
            default: 'bg-card text-card-foreground',
            destructive:
                'text-destructive bg-card [&>svg]:text-current *:data-[slot=alert-description]:text-destructive/90',
        },
    },
    defaultVariants: {
        variant: 'default',
    },
});

export interface BitAlertProps extends React.ComponentProps<'div'>, VariantProps<typeof alertVariants> {}

function Alert({ children, className, font, variant, ...props }: BitAlertProps) {
    return (
        <div className="relative">
            <ShadcnAlert
                {...props}
                variant={variant}
                className={cn(
                    'relative rounded-none border-none bg-background',
                    font !== 'normal' && 'retro',
                    className
                )}
            >
                {children}
            </ShadcnAlert>

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

function AlertTitle({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
    return <ShadcnAlertTitle className={cn('line-clamp-1 font-medium tracking-tight', className)} {...props} />;
}

function AlertDescription({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
    return (
        <ShadcnAlertDescription
            className={cn(
                'text-muted-foreground grid justify-items-start gap-1 text-sm [&_p]:leading-relaxed',
                className
            )}
            {...props}
        />
    );
}

export { Alert, AlertTitle, AlertDescription };
