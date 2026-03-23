import { describe, it, expect } from 'vitest';
import { MapCitySelector } from './map-city-selector';

describe('MapCitySelector', () => {
    it('exports MapCitySelector component', () => {
        expect(MapCitySelector).toBeDefined();
        expect(typeof MapCitySelector).toBe('function');
    });
});
