import { describe, it, expect } from 'vitest';

/**
 * 8bitcn 컴포넌트 import 검증 테스트.
 * shadcn → 8bitcn 마이그레이션 후 모든 래퍼 컴포넌트가 올바르게 export되는지 확인.
 */
describe('8bitcn component exports', () => {
    it('button exports Button', async () => {
        const mod = await import('./button');
        expect(mod.Button).toBeDefined();
    });

    it('card exports Card, CardHeader, CardContent, CardTitle, CardFooter', async () => {
        const mod = await import('./card');
        expect(mod.Card).toBeDefined();
        expect(mod.CardHeader).toBeDefined();
        expect(mod.CardContent).toBeDefined();
        expect(mod.CardTitle).toBeDefined();
        expect(mod.CardFooter).toBeDefined();
    });

    it('tabs exports Tabs, TabsList, TabsTrigger, TabsContent', async () => {
        const mod = await import('./tabs');
        expect(mod.Tabs).toBeDefined();
        expect(mod.TabsList).toBeDefined();
        expect(mod.TabsTrigger).toBeDefined();
        expect(mod.TabsContent).toBeDefined();
    });

    it('select exports Select, SelectTrigger, SelectValue, SelectContent, SelectItem', async () => {
        const mod = await import('./select');
        expect(mod.Select).toBeDefined();
        expect(mod.SelectTrigger).toBeDefined();
        expect(mod.SelectValue).toBeDefined();
        expect(mod.SelectContent).toBeDefined();
        expect(mod.SelectItem).toBeDefined();
    });

    it('badge exports Badge', async () => {
        const mod = await import('./badge');
        expect(mod.Badge).toBeDefined();
    });

    it('input exports Input', async () => {
        const mod = await import('./input');
        expect(mod.Input).toBeDefined();
    });

    it('progress exports Progress', async () => {
        const mod = await import('./progress');
        expect(mod.Progress).toBeDefined();
    });

    it('toast exports toast function', async () => {
        const mod = await import('./toast');
        expect(mod.toast).toBeDefined();
    });

    it('game components export correctly', async () => {
        const health = await import('./health-bar');
        expect(health.default).toBeDefined();

        const mana = await import('./mana-bar');
        expect(mana.default).toBeDefined();

        const xp = await import('./xp-bar');
        expect(xp.default).toBeDefined();
    });
});
