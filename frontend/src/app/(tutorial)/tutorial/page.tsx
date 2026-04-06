'use client';

import { useRouter } from 'next/navigation';
import { useTutorialStore } from '@/stores/tutorialStore';
import { Button } from '@/components/ui/8bit/button';
import { Card, CardContent } from '@/components/ui/8bit/card';

export default function TutorialIntroPage() {
    const router = useRouter();
    const { nextStep, steps } = useTutorialStore();
    const step = steps[0];

    const handleStart = () => {
        nextStep();
        router.push('/tutorial/create');
    };

    return (
        <div className="flex items-center justify-center min-h-screen p-4">
            <Card className="max-w-lg w-full">
                <CardContent className="pt-6 space-y-6 text-center">
                    <h1 className="text-2xl font-bold">{step?.title ?? '오픈은하영웅전설 튜토리얼'}</h1>
                    <p className="text-muted-foreground leading-relaxed">
                        {step?.description ??
                            '게임의 전체 흐름을 체험합니다. 장교 생성부터 전투, 외교까지 단계별로 안내해 드립니다.'}
                    </p>

                    <div className="space-y-3 text-left text-sm">
                        <div className="flex items-start gap-2">
                            <span className="font-bold text-primary shrink-0">1.</span>
                            <span>장교를 생성하고 능력치를 배분합니다</span>
                        </div>
                        <div className="flex items-start gap-2">
                            <span className="font-bold text-primary shrink-0">2.</span>
                            <span>행성 관리 커맨드로 행성을 발전시킵니다</span>
                        </div>
                        <div className="flex items-start gap-2">
                            <span className="font-bold text-primary shrink-0">3.</span>
                            <span>병사를 징병하고 훈련시켜 전투에 참여합니다</span>
                        </div>
                        <div className="flex items-start gap-2">
                            <span className="font-bold text-primary shrink-0">4.</span>
                            <span>외교를 통해 다른 진영와 교류합니다</span>
                        </div>
                        <div className="flex items-start gap-2">
                            <span className="font-bold text-primary shrink-0">5.</span>
                            <span>진영을 운영하고 은하통일을 노립니다</span>
                        </div>
                    </div>

                    <Button className="w-full" size="lg" onClick={handleStart}>
                        시작하기
                    </Button>

                    <p className="text-xs text-muted-foreground">
                        서버 접속 없이 진행됩니다. 언제든 건너뛰기가 가능합니다.
                    </p>
                </CardContent>
            </Card>
        </div>
    );
}
