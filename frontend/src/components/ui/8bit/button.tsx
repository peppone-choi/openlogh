import { type VariantProps, cva } from 'class-variance-authority';

import { cn } from '@/lib/utils';

import { Button as ShadcnButton } from '@/components/ui/button';

import '@/components/ui/8bit/styles/retro.css';

export const buttonVariants = cva('', {
    variants: {
        font: {
            normal: '',
            retro: 'retro',
        },
        variant: {
            default: 'bg-foreground',
            destructive: 'bg-foreground',
            outline: 'bg-foreground',
            secondary: 'bg-secondary text-secondary-foreground hover:bg-secondary/80',
            ghost: 'hover:bg-accent hover:text-accent-foreground',
            link: 'text-primary underline-offset-4 hover:underline',
        },
        size: {
            default: '',
            sm: '',
            lg: '',
            icon: '',
            'icon-sm': '',
            'icon-xs': '',
        },
    },
    defaultVariants: {
        variant: 'default',
        size: 'default',
    },
});

export interface BitButtonProps
    extends React.ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> {
    asChild?: boolean;
    ref?: React.Ref<HTMLButtonElement>;
}

function Button({ children, asChild, ...props }: BitButtonProps) {
    const { variant, size, className, font } = props;

    return (
        <ShadcnButton
            {...props}
            className={cn(
                'rounded-none active:translate-y-1 transition-transform relative inline-flex items-center justify-center gap-1.5 border-none',
                size === 'icon' && 'mx-1 my-0',
                font !== 'normal' && 'retro',
                className
            )}
            size={size}
            variant={variant}
            asChild={asChild}
        >
            {asChild ? (
                <span className="relative inline-flex items-center justify-center gap-1.5">
                    {children}

                    {variant !== 'ghost' && variant !== 'link' && size !== 'icon' && (
                        <>
                            {/* Pixelated border */}
                            <div className="absolute -top-0.5 w-1/2 left-0.5 h-0.5 bg-foreground/20" />
                            <div className="absolute -top-0.5 w-1/2 right-0.5 h-0.5 bg-foreground/20" />
                            <div className="absolute -bottom-0.5 w-1/2 left-0.5 h-0.5 bg-foreground/20" />
                            <div className="absolute -bottom-0.5 w-1/2 right-0.5 h-0.5 bg-foreground/20" />
                            <div className="absolute top-0 left-0 size-0.5 bg-foreground/20" />
                            <div className="absolute top-0 right-0 size-0.5 bg-foreground/20" />
                            <div className="absolute bottom-0 left-0 size-0.5 bg-foreground/20" />
                            <div className="absolute bottom-0 right-0 size-0.5 bg-foreground/20" />
                            <div className="absolute top-0.5 -left-0.5 h-[calc(100%-4px)] w-0.5 bg-foreground/20" />
                            <div className="absolute top-0.5 -right-0.5 h-[calc(100%-4px)] w-0.5 bg-foreground/20" />
                            {variant !== 'outline' && (
                                <>
                                    {/* Top shadow */}
                                    <div className="absolute top-0 left-0 w-full h-0.5 bg-foreground/8" />
                                    <div className="absolute top-0.5 left-0 w-1 h-0.5 bg-foreground/8" />

                                    {/* Bottom shadow */}
                                    <div className="absolute bottom-0 left-0 w-full h-0.5 bg-foreground/8" />
                                    <div className="absolute bottom-0.5 right-0 w-1 h-0.5 bg-foreground/8" />
                                </>
                            )}
                        </>
                    )}

                    {size === 'icon' && (
                        <>
                            <div className="absolute top-0 left-0 w-full h-px md:h-0.5 bg-foreground/20 pointer-events-none" />
                            <div className="absolute bottom-0 w-full h-px md:h-0.5 bg-foreground/20 pointer-events-none" />
                            <div className="absolute top-0.5 -left-0.5 w-px md:w-0.5 h-1/2 bg-foreground/20 pointer-events-none" />
                            <div className="absolute bottom-0.5 -left-0.5 w-px md:w-0.5 h-1/2 bg-foreground/20 pointer-events-none" />
                            <div className="absolute top-0.5 -right-0.5 w-px md:w-0.5 h-1/2 bg-foreground/20 pointer-events-none" />
                            <div className="absolute bottom-0.5 -right-0.5 w-px md:w-0.5 h-1/2 bg-foreground/20 pointer-events-none" />
                        </>
                    )}
                </span>
            ) : (
                <>
                    {children}

                    {variant !== 'ghost' && variant !== 'link' && size !== 'icon' && (
                        <>
                            {/* Pixelated border */}
                            <div className="absolute -top-0.5 w-1/2 left-0.5 h-0.5 bg-foreground/20" />
                            <div className="absolute -top-0.5 w-1/2 right-0.5 h-0.5 bg-foreground/20" />
                            <div className="absolute -bottom-0.5 w-1/2 left-0.5 h-0.5 bg-foreground/20" />
                            <div className="absolute -bottom-0.5 w-1/2 right-0.5 h-0.5 bg-foreground/20" />
                            <div className="absolute top-0 left-0 size-0.5 bg-foreground/20" />
                            <div className="absolute top-0 right-0 size-0.5 bg-foreground/20" />
                            <div className="absolute bottom-0 left-0 size-0.5 bg-foreground/20" />
                            <div className="absolute bottom-0 right-0 size-0.5 bg-foreground/20" />
                            <div className="absolute top-0.5 -left-0.5 h-[calc(100%-4px)] w-0.5 bg-foreground/20" />
                            <div className="absolute top-0.5 -right-0.5 h-[calc(100%-4px)] w-0.5 bg-foreground/20" />
                            {variant !== 'outline' && (
                                <>
                                    {/* Top shadow */}
                                    <div className="absolute top-0 left-0 w-full h-0.5 bg-foreground/8" />
                                    <div className="absolute top-0.5 left-0 w-1 h-0.5 bg-foreground/8" />

                                    {/* Bottom shadow */}
                                    <div className="absolute bottom-0 left-0 w-full h-0.5 bg-foreground/8" />
                                    <div className="absolute bottom-0.5 right-0 w-1 h-0.5 bg-foreground/8" />
                                </>
                            )}
                        </>
                    )}

                    {size === 'icon' && (
                        <>
                            <div className="absolute top-0 left-0 w-full h-px md:h-0.5 bg-foreground/20 pointer-events-none" />
                            <div className="absolute bottom-0 w-full h-px md:h-0.5 bg-foreground/20 pointer-events-none" />
                            <div className="absolute top-0.5 -left-0.5 w-px md:w-0.5 h-1/2 bg-foreground/20 pointer-events-none" />
                            <div className="absolute bottom-0.5 -left-0.5 w-px md:w-0.5 h-1/2 bg-foreground/20 pointer-events-none" />
                            <div className="absolute top-0.5 -right-0.5 w-px md:w-0.5 h-1/2 bg-foreground/20 pointer-events-none" />
                            <div className="absolute bottom-0.5 -right-0.5 w-px md:w-0.5 h-1/2 bg-foreground/20 pointer-events-none" />
                        </>
                    )}
                </>
            )}
        </ShadcnButton>
    );
}

export { Button };
