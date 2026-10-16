import { describe, expect, it } from 'vitest';
import {
  EMPTY_VALUE,
  formatBytes,
  formatCount,
  formatDateTime,
  formatDecimalCount,
  formatPercent,
  formatRatioAsPercent,
} from '../format.util';

const NBSP = String.fromCodePoint(0xa0);

describe('format', () => {
  it.each([
    [0, '0 B'],
    [1023, '1023 B'],
    [1024, '1 KiB'],
    [77_752, '75,9 KiB'],
    [5 * 1024 ** 3, '5 GiB'],
    [1.5 * 1024 ** 4, '1,5 TiB'],
  ])('formats %d bytes as %s', (bytes, expected) => {
    expect(formatBytes(bytes)).toBe(expected.replace(' ', NBSP));
  });

  it('groups thousands the Polish way', () => {
    expect(formatCount(176_755)).toBe(`176${NBSP}755`);
    expect(formatCount(1000)).toBe('1000');
  });

  it('formats decimal totals beyond Number.MAX_SAFE_INTEGER without losing digits', () => {
    expect(formatDecimalCount('9007199254740993')).toBe(
      ['9', '007', '199', '254', '740', '993'].join(NBSP),
    );
  });

  it('formats percentages and CTR ratios', () => {
    expect(formatPercent(42)).toBe('42%');
    expect(formatRatioAsPercent('0.026251')).toBe('2,6251%');
    expect(formatRatioAsPercent('1.000000')).toBe('100,00%');
    expect(formatRatioAsPercent(null)).toBe(EMPTY_VALUE);
  });

  it('formats timestamps with Polish date and time', () => {
    expect(formatDateTime('2026-10-14T10:15:30.123Z', 'UTC')).toBe('14.10.2026, 10:15:30');
    expect(formatDateTime(null)).toBe(EMPTY_VALUE);
  });
});
