'use client';

import { useEffect, useRef } from 'react';
import { useTutorialStore } from '@/stores/tutorialStore';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { useGameStore } from '@/stores/gameStore';
import { registerTutorialApiGuard, ejectTutorialApiGuard } from '@/lib/tutorial-api-guard';
import { MOCK_WORLD, MOCK_MY_GENERAL, MOCK_GENERALS, MOCK_CITIES, MOCK_NATIONS, MOCK_DIPLOMACY } from '@/data/tutorial';
import { MOCK_DIPLOMACY_AFTER_LETTER } from '@/data/tutorial/mock-diplomacy';
import { GuideOverlay } from './guide-overlay';
import { StepController } from './step-controller';

interface TutorialProviderProps {
    children: React.ReactNode;
}

export function TutorialProvider({ children }: TutorialProviderProps) {
    const { start, currentStep, steps } = useTutorialStore();
    const snapshotRef = useRef<{
        world: ReturnType<typeof useWorldStore.getState>;
        general: ReturnType<typeof useOfficerStore.getState>;
        game: ReturnType<typeof useGameStore.getState>;
    } | null>(null);

    // 1) Store Seeding + API Guard — mount 시 1회
    useEffect(() => {
        // Save snapshot for cleanup
        snapshotRef.current = {
            world: useWorldStore.getState(),
            general: useOfficerStore.getState(),
            game: useGameStore.getState(),
        };

        // Start tutorial
        start();

        // Seed stores with mock data
        useWorldStore.setState({
            currentWorld: MOCK_WORLD,
            worlds: [MOCK_WORLD],
        });
        useOfficerStore.setState({
            myOfficer: MOCK_MY_GENERAL,
            officers: MOCK_GENERALS,
        });
        useGameStore.setState({
            cities: MOCK_CITIES,
            nations: MOCK_NATIONS,
            generals: MOCK_GENERALS,
            diplomacy: MOCK_DIPLOMACY,
        });

        // Register API guard
        registerTutorialApiGuard();

        return () => {
            // Cleanup: remove guard + restore stores
            ejectTutorialApiGuard();
            useTutorialStore.getState().exit();

            if (snapshotRef.current) {
                useWorldStore.setState(snapshotRef.current.world);
                useOfficerStore.setState(snapshotRef.current.general);
                useGameStore.setState(snapshotRef.current.game);
            }
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // 2) Step 변화 시 onEnter 로직 (단계별 데이터 변화)
    useEffect(() => {
        const step = steps[currentStep];
        if (!step) return;

        switch (step.id) {
            case 3: {
                // 장교 생성 완료 — myOfficer 확인
                useOfficerStore.setState({ myOfficer: MOCK_MY_GENERAL });
                break;
            }
            case 6: {
                // 개간 결과 — 행성 생산 수치 증가
                const cities = useGameStore
                    .getState()
                    .cities.map((c) => (c.id === -1 ? { ...c, agri: c.agri + 100 } : c));
                useGameStore.setState({ cities });
                break;
            }
            case 8: {
                // 징병 결과 — 장교 함선 증가
                const generals8 = useGameStore
                    .getState()
                    .generals.map((g) => (g.id === -1 ? { ...g, crew: g.crew + 500 } : g));
                useGameStore.setState({ generals: generals8 });
                const myGen8 = useOfficerStore.getState().myOfficer;
                if (myGen8 && myGen8.id === -1) {
                    useOfficerStore.setState({ myOfficer: { ...myGen8, crew: myGen8.crew + 500 } });
                }
                break;
            }
            case 9: {
                // 훈련 결과 — 훈련도 증가
                const generals9 = useGameStore
                    .getState()
                    .generals.map((g) => (g.id === -1 ? { ...g, train: Math.min(100, g.train + 20) } : g));
                useGameStore.setState({ generals: generals9 });
                const myGen9 = useOfficerStore.getState().myOfficer;
                if (myGen9 && myGen9.id === -1) {
                    useOfficerStore.setState({
                        myOfficer: { ...myGen9, train: Math.min(100, myGen9.train + 20) },
                    });
                }
                break;
            }
            case 12: {
                // 점령 결과 — 한중 소속 변경
                const cities12 = useGameStore.getState().cities.map((c) => (c.id === -4 ? { ...c, nationId: -1 } : c));
                useGameStore.setState({ cities: cities12 });
                break;
            }
            case 15: {
                // 서신 발송 — 외교 관계 업데이트
                useGameStore.setState({ diplomacy: MOCK_DIPLOMACY_AFTER_LETTER });
                break;
            }
        }
    }, [currentStep, steps]);

    return (
        <>
            {children}
            <GuideOverlay />
            <StepController />
        </>
    );
}
