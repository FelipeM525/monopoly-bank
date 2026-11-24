import { describe, expect, it } from 'vitest';
import {
  formatCents,
  formatCentsForInput,
  parseReaisToCents,
} from './money';

describe('money utils (integer-only centavos)', () => {
  describe('formatCents', () => {
    it('formats zero, single-digit centavos, and whole reais in pt-BR format', () => {
      expect(formatCents(0)).toBe('R$ 0,00');
      expect(formatCents(5)).toBe('R$ 0,05');
      expect(formatCents(50)).toBe('R$ 0,50');
      expect(formatCents(12_345)).toBe('R$ 123,45');
      expect(formatCents(150_000)).toBe('R$ 1.500,00');
      expect(formatCents(2_000_000)).toBe('R$ 20.000,00');
    });

    it('formats negative centavos with a leading minus sign', () => {
      expect(formatCents(-20_050)).toBe('-R$ 200,50');
    });

    it('rejects non-integer floating-point numbers', () => {
      expect(() => formatCents(12.34)).toThrow(/safe integer/i);
      expect(() => formatCents(Number.NaN)).toThrow(/safe integer/i);
    });
  });

  describe('formatCentsForInput', () => {
    it('formats centavos into a plain decimal string without currency symbols', () => {
      expect(formatCentsForInput(0)).toBe('0,00');
      expect(formatCentsForInput(12_345)).toBe('123,45');
      expect(formatCentsForInput(150_000)).toBe('1500,00');
      expect(formatCentsForInput(-500)).toBe('-5,00');
    });
  });

  describe('parseReaisToCents', () => {
    it('parses comma-separated and dot-separated decimal values accurately without float drift', () => {
      expect(parseReaisToCents('123,45')).toBe(12_345);
      expect(parseReaisToCents('123.45')).toBe(12_345);
      // Classic IEEE-754 float trap: 19.99 * 100 in float is 1998.9999999999998
      expect(parseReaisToCents('19,99')).toBe(1_999);
      expect(parseReaisToCents('19.99')).toBe(1_999);
      expect(parseReaisToCents('0,05')).toBe(5);
      expect(parseReaisToCents('10,5')).toBe(1_050);
      expect(parseReaisToCents('200')).toBe(20_000);
      expect(parseReaisToCents('200,')).toBe(20_000);
    });

    it('parses formatted Brazilian Reais strings with R$ prefix and thousands separators', () => {
      expect(parseReaisToCents('R$ 1.500,00')).toBe(150_000);
      expect(parseReaisToCents('  R$ 20.000,50  ')).toBe(2_000_050);
      expect(parseReaisToCents('1.500')).toBe(150_000);
    });

    it('returns null for empty, whitespace, or invalid strings', () => {
      expect(parseReaisToCents('')).toBeNull();
      expect(parseReaisToCents('   ')).toBeNull();
      expect(parseReaisToCents('abc')).toBeNull();
      expect(parseReaisToCents('12,345')).toBeNull();
      expect(parseReaisToCents('12,3,4')).toBeNull();
    });
  });
});
