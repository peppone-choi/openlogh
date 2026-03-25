'use client';

import { useState, useMemo } from 'react';
import { Button } from '@/components/ui/8bit/button';
import { Input } from '@/components/ui/8bit/input';
import { Badge } from '@/components/ui/8bit/badge';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/8bit/tabs';
import { cn } from '@/lib/utils';
import { useGeneralStore } from '@/stores/generalStore';

// ── Korean consonant (초성) search ──

const CHOSUNG = [
    'ㄱ',
    'ㄲ',
    'ㄴ',
    'ㄷ',
    'ㄸ',
    'ㄹ',
    'ㅁ',
    'ㅂ',
    'ㅃ',
    'ㅅ',
    'ㅆ',
    'ㅇ',
    'ㅈ',
    'ㅉ',
    'ㅊ',
    'ㅋ',
    'ㅌ',
    'ㅍ',
    'ㅎ',
];

function getChosung(char: string): string | null {
    const code = char.charCodeAt(0) - 0xac00;
    if (code < 0 || code > 11171) return null;
    return CHOSUNG[Math.floor(code / 588)];
}

function matchesChosungSearch(text: string, query: string): boolean {
    if (!query) return true;
    // If query is all chosung consonants, match against text's chosung
    const isChosungQuery = [...query].every((c) => CHOSUNG.includes(c));
    if (isChosungQuery) {
        const textChosung = [...text].map((c) => getChosung(c) ?? c).join('');
        return textChosung.includes(query);
    }
    // Otherwise plain substring match
    return text.toLowerCase().includes(query.toLowerCase());
}

// ── Item type definitions ──

type ItemCategory = 'weapon' | 'book' | 'horse' | 'item';

const CATEGORY_INFO: Record<ItemCategory, { label: string; emoji: string }> = {
    weapon: { label: '무기', emoji: '⚔️' },
    book: { label: '서적', emoji: '📚' },
    horse: { label: '명마', emoji: '🐎' },
    item: { label: '특수', emoji: '💎' },
};

interface EquipmentItem {
    code: string;
    name: string;
    category: ItemCategory;
    cost: number;
    reqSecu: number;
    attack?: number;
    defence?: number;
    intel?: number;
    leadership?: number;
    speed?: number;
    info: string;
}

// Placeholder items — in production these come from server/game constants
const EQUIPMENT_ITEMS: EquipmentItem[] = [
    // 무기
    {
        code: 'S_sword',
        name: '검',
        category: 'weapon',
        cost: 100,
        reqSecu: 0,
        attack: 1,
        info: '기본 검',
    },
    {
        code: 'S_spear',
        name: '창',
        category: 'weapon',
        cost: 200,
        reqSecu: 10,
        attack: 2,
        info: '일반 창',
    },
    {
        code: 'S_halberd',
        name: '극',
        category: 'weapon',
        cost: 400,
        reqSecu: 20,
        attack: 3,
        info: '강력한 극',
    },
    {
        code: 'S_blade',
        name: '도',
        category: 'weapon',
        cost: 800,
        reqSecu: 30,
        attack: 5,
        info: '날카로운 도',
    },
    {
        code: 'S_bow',
        name: '궁',
        category: 'weapon',
        cost: 600,
        reqSecu: 25,
        attack: 4,
        info: '정교한 궁',
    },
    {
        code: 'S_greatsword',
        name: '대검',
        category: 'weapon',
        cost: 1500,
        reqSecu: 50,
        attack: 7,
        info: '명검',
    },
    {
        code: 'S_fang_tian',
        name: '방천화극',
        category: 'weapon',
        cost: 3000,
        reqSecu: 70,
        attack: 10,
        info: '전설의 방천화극',
    },
    {
        code: 'S_green_dragon',
        name: '청룡언월도',
        category: 'weapon',
        cost: 3000,
        reqSecu: 70,
        attack: 10,
        info: '관우의 청룡언월도',
    },
    {
        code: 'S_double_sword',
        name: '쌍검',
        category: 'weapon',
        cost: 2000,
        reqSecu: 60,
        attack: 8,
        info: '쌍검',
    },
    // 서적
    {
        code: 'B_basic',
        name: '맹덕신서',
        category: 'book',
        cost: 200,
        reqSecu: 10,
        intel: 2,
        info: '기본 병서',
    },
    {
        code: 'B_art_of_war',
        name: '손자병법',
        category: 'book',
        cost: 1000,
        reqSecu: 40,
        intel: 5,
        info: '손무의 병법서',
    },
    {
        code: 'B_36',
        name: '삼십육계',
        category: 'book',
        cost: 800,
        reqSecu: 35,
        intel: 4,
        info: '36가지 계략',
    },
    {
        code: 'B_taigong',
        name: '태공병법',
        category: 'book',
        cost: 1500,
        reqSecu: 50,
        intel: 7,
        info: '태공망의 병법',
    },
    {
        code: 'B_dunjia',
        name: '둔갑천서',
        category: 'book',
        cost: 3000,
        reqSecu: 70,
        intel: 10,
        info: '전설의 도술서',
    },
    {
        code: 'B_politics',
        name: '논어',
        category: 'book',
        cost: 500,
        reqSecu: 20,
        intel: 3,
        info: '정치의 기본서',
    },
    // 명마
    {
        code: 'H_basic',
        name: '산마',
        category: 'horse',
        cost: 200,
        reqSecu: 10,
        speed: 1,
        info: '기본 말',
    },
    {
        code: 'H_fast',
        name: '비전마',
        category: 'horse',
        cost: 500,
        reqSecu: 25,
        speed: 2,
        info: '빠른 전마',
    },
    {
        code: 'H_red_hare',
        name: '적토마',
        category: 'horse',
        cost: 3000,
        reqSecu: 70,
        speed: 5,
        info: '전설의 적토마',
    },
    {
        code: 'H_shadow',
        name: '절영',
        category: 'horse',
        cost: 2000,
        reqSecu: 60,
        speed: 4,
        info: '그림자처럼 빠른 말',
    },
    {
        code: 'H_storm',
        name: '적로',
        category: 'horse',
        cost: 1500,
        reqSecu: 50,
        speed: 3,
        info: '유비의 적로',
    },
    // 특수
    {
        code: 'I_shield',
        name: '방패',
        category: 'item',
        cost: 300,
        reqSecu: 15,
        defence: 2,
        info: '기본 방패',
    },
    {
        code: 'I_armor',
        name: '갑옷',
        category: 'item',
        cost: 800,
        reqSecu: 35,
        defence: 4,
        info: '튼튼한 갑옷',
    },
    {
        code: 'I_jade',
        name: '옥쇄',
        category: 'item',
        cost: 2000,
        reqSecu: 60,
        leadership: 3,
        info: '전국옥쇄',
    },
    {
        code: 'I_wine',
        name: '두강주',
        category: 'item',
        cost: 100,
        reqSecu: 0,
        info: '사기 회복용 술',
    },
];

interface EquipmentBrowserProps {
    commandName: string;
    citySecu?: number;
    gold?: number;
    onSubmit: (itemType: string, itemCode: string) => void;
}

export function EquipmentBrowser({ commandName, citySecu, gold, onSubmit }: EquipmentBrowserProps) {
    const { myGeneral } = useGeneralStore();
    const [search, setSearch] = useState('');
    const [selectedCategory, setSelectedCategory] = useState<ItemCategory | 'sell'>('weapon');
    const [selectedItem, setSelectedItem] = useState<EquipmentItem | null>(null);
    const [mode, setMode] = useState<'buy' | 'sell'>('buy');

    const currentGold = gold ?? myGeneral?.gold ?? 0;
    const currentSecu = citySecu ?? 100;

    // Owned items from general
    const ownedItems = useMemo(() => {
        if (!myGeneral) return {};
        return {
            weapon: myGeneral.weaponCode,
            book: myGeneral.bookCode,
            horse: myGeneral.horseCode,
            item: myGeneral.itemCode,
        };
    }, [myGeneral]);

    const filteredItems = useMemo(() => {
        if (selectedCategory === 'sell') return [];
        return EQUIPMENT_ITEMS.filter((item) => {
            if (item.category !== selectedCategory) return false;
            if (search && !matchesChosungSearch(item.name, search)) return false;
            return true;
        });
    }, [selectedCategory, search]);

    const handleSelect = (item: EquipmentItem) => {
        setSelectedItem(item);
        setMode('buy');
    };

    const handleSubmit = () => {
        if (mode === 'sell' && selectedCategory !== 'sell') {
            // Sell current equipment of selected category
            onSubmit(selectedCategory, 'NONE');
        } else if (selectedItem) {
            onSubmit(selectedItem.category, selectedItem.code);
        }
    };

    const isUnavailable = (item: EquipmentItem) => item.reqSecu > currentSecu || item.cost > currentGold;

    return (
        <div className="space-y-3">
            {/* Header info */}
            <div className="rounded-md bg-amber-900/20 border border-amber-800/40 px-3 py-2 text-xs text-amber-200/90">
                장비를 구입하거나 매각합니다.
                <span className="text-red-400"> 붉은색</span>은 현재 구입 불가.
                <div className="mt-1 flex gap-3">
                    <span>
                        치안: <strong>{currentSecu.toLocaleString()}</strong>
                    </span>
                    <span>
                        자금: <strong className="text-amber-300">{currentGold.toLocaleString()}금</strong>
                    </span>
                </div>
            </div>

            {/* Search */}
            <Input
                placeholder="🔍 장비 검색 (초성 가능: ㅈㄹㅇㅇㄷ)"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="text-xs h-8"
            />

            {/* Category tabs */}
            <Tabs
                value={selectedCategory}
                onValueChange={(v) => {
                    setSelectedCategory(v as ItemCategory | 'sell');
                    setSelectedItem(null);
                }}
            >
                <TabsList className="w-full grid grid-cols-5 h-8">
                    {(Object.entries(CATEGORY_INFO) as [ItemCategory, typeof CATEGORY_INFO.weapon][]).map(
                        ([key, { label, emoji }]) => (
                            <TabsTrigger key={key} value={key} className="text-[10px] px-1">
                                {emoji} {label}
                            </TabsTrigger>
                        )
                    )}
                    <TabsTrigger value="sell" className="text-[10px] px-1">
                        💰 판매
                    </TabsTrigger>
                </TabsList>

                {/* Buy tabs */}
                {(Object.keys(CATEGORY_INFO) as ItemCategory[]).map((cat) => (
                    <TabsContent key={cat} value={cat} className="mt-2 space-y-1 max-h-60 overflow-y-auto">
                        {filteredItems.length === 0 ? (
                            <p className="text-xs text-muted-foreground text-center py-4">
                                {search ? '검색 결과 없음' : '아이템 없음'}
                            </p>
                        ) : (
                            filteredItems.map((item) => {
                                const unavailable = isUnavailable(item);
                                const isSelected = selectedItem?.code === item.code;
                                return (
                                    <button
                                        key={item.code}
                                        onClick={() => handleSelect(item)}
                                        className={cn(
                                            'w-full text-left px-3 py-2 rounded-md border text-xs transition-colors',
                                            unavailable
                                                ? 'border-red-800/40 text-red-400/80 bg-red-950/20'
                                                : isSelected
                                                  ? 'border-amber-500 bg-amber-900/30 text-amber-100'
                                                  : 'border-border hover:border-amber-700/50 hover:bg-amber-900/10'
                                        )}
                                    >
                                        <div className="flex items-center justify-between">
                                            <div className="flex items-center gap-2">
                                                <span className="font-medium">{item.name}</span>
                                                {unavailable && (
                                                    <Badge variant="destructive" className="text-[9px] px-1 py-0">
                                                        불가
                                                    </Badge>
                                                )}
                                            </div>
                                            <span className="text-amber-400/80">{item.cost.toLocaleString()}금</span>
                                        </div>
                                        <div className="flex gap-2 mt-0.5 text-[10px] text-muted-foreground">
                                            {item.attack && <span>공격+{item.attack}</span>}
                                            {item.defence && <span>방어+{item.defence}</span>}
                                            {item.intel && <span>지력+{item.intel}</span>}
                                            {item.leadership && <span>통솔+{item.leadership}</span>}
                                            {item.speed && <span>속도+{item.speed}</span>}
                                            <span className="ml-auto">치안 {item.reqSecu}+</span>
                                        </div>
                                    </button>
                                );
                            })
                        )}
                    </TabsContent>
                ))}

                {/* Sell tab */}
                <TabsContent value="sell" className="mt-2 space-y-1">
                    <p className="text-xs text-muted-foreground mb-2">
                        보유 장비를 매각합니다. (매각가 = 구매가의 50%)
                    </p>
                    {(Object.entries(CATEGORY_INFO) as [ItemCategory, typeof CATEGORY_INFO.weapon][]).map(
                        ([cat, { label, emoji }]) => {
                            const ownedCode = ownedItems[cat as ItemCategory];
                            const ownedItem = EQUIPMENT_ITEMS.find((i) => i.code === ownedCode);
                            const isEmpty = !ownedCode || ownedCode === 'NONE' || ownedCode === '';
                            return (
                                <button
                                    key={cat}
                                    disabled={isEmpty}
                                    onClick={() => {
                                        setSelectedCategory(cat as ItemCategory);
                                        setMode('sell');
                                        setSelectedItem(ownedItem ?? null);
                                    }}
                                    className={cn(
                                        'w-full text-left px-3 py-2 rounded-md border text-xs',
                                        isEmpty
                                            ? 'border-border/30 text-muted-foreground/50 cursor-not-allowed'
                                            : 'border-border hover:border-amber-700/50 hover:bg-amber-900/10'
                                    )}
                                >
                                    <div className="flex justify-between">
                                        <span>
                                            {emoji} {label}: {ownedItem ? ownedItem.name : isEmpty ? '없음' : ownedCode}
                                        </span>
                                        {ownedItem && (
                                            <span className="text-green-400">
                                                {Math.floor(ownedItem.cost / 2).toLocaleString()}금
                                            </span>
                                        )}
                                    </div>
                                </button>
                            );
                        }
                    )}
                </TabsContent>
            </Tabs>

            {/* Selected item detail */}
            {selectedItem && (
                <div className="rounded-md border border-amber-800/40 bg-amber-950/20 px-3 py-2 text-xs">
                    <div className="flex justify-between items-center">
                        <span className="font-medium text-amber-200">{selectedItem.name}</span>
                        <Badge variant="outline" className="text-[9px]">
                            {mode === 'buy' ? '구매' : '판매'}
                        </Badge>
                    </div>
                    <p className="text-muted-foreground mt-1">{selectedItem.info}</p>
                    <div className="flex gap-2 mt-1 text-[10px]">
                        {selectedItem.attack && <span>⚔️ 공격+{selectedItem.attack}</span>}
                        {selectedItem.defence && <span>🛡️ 방어+{selectedItem.defence}</span>}
                        {selectedItem.intel && <span>📖 지력+{selectedItem.intel}</span>}
                        {selectedItem.leadership && <span>👑 통솔+{selectedItem.leadership}</span>}
                        {selectedItem.speed && <span>🏇 속도+{selectedItem.speed}</span>}
                    </div>
                </div>
            )}

            {/* Submit */}
            <Button size="sm" onClick={handleSubmit} disabled={!selectedItem && mode === 'buy'} className="w-full">
                {mode === 'sell' ? '판매' : '구매'} 확인
            </Button>
        </div>
    );
}
