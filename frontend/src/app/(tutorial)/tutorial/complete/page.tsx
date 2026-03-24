'use client';

import { useRouter } from 'next/navigation';
import { useTutorialStore } from '@/stores/tutorialStore';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';

export default function TutorialCompletePage() {
    const router = useRouter();
    const { exit } = useTutorialStore();

    const handleReturn = () => {
        exit();
        router.push('/lobby');
    };

    return (
        <div className="flex items-center justify-center min-h-screen p-4">
            <Card className="max-w-lg w-full">
                <CardContent className="pt-6 space-y-6 text-center">
                    <div className="text-5xl">&#127942;</div>
                    <h1 className="text-2xl font-bold">튜토리얼 완료!</h1>
                    <p className="text-muted-foreground leading-relaxed">
                        축하합니다! 오픈삼국의 기본 플로우를 모두 체험했습니다.
                    </p>

                    <div className="space-y-2 text-left text-sm bg-muted/50 rounded-lg p-4">
                        <p className="font-semibold mb-2">학습한 내용:</p>
                        <div className="space-y-1 text-muted-foreground">
                            <p>- 장수 생성과 능력치(통/무/지/정/매) 배분</p>
                            <p>- 내정 커맨드로 도시 발전 (개간, 상업투자 등)</p>
                            <p>- 군사 커맨드 (징병, 훈련, 출진)</p>
                            <p>- 전투 시스템과 도시 점령</p>
                            <p>- 외교 관계와 서신 교환</p>
                            <p>- 국가 운영 (세력 정보, 인사부, 내무부)</p>
                        </div>
                    </div>

                    <p className="text-sm text-muted-foreground">이제 실제 서버에 참여하여 천하를 통일해 보세요!</p>

                    <Button className="w-full" size="lg" onClick={handleReturn}>
                        로비로 돌아가기
                    </Button>
                </CardContent>
            </Card>
        </div>
    );
}
