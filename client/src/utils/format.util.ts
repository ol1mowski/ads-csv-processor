const LOCALE = 'pl-PL';
const BYTE_UNITS = ['B', 'KiB', 'MiB', 'GiB', 'TiB'] as const;
const NO_BREAK_SPACE = String.fromCodePoint(0xa0);

export const EMPTY_VALUE = '—';

const integerFormat = new Intl.NumberFormat(LOCALE, { maximumFractionDigits: 0 });
const scaledBytesFormat = new Intl.NumberFormat(LOCALE, { maximumFractionDigits: 1 });
const wholePercentFormat = new Intl.NumberFormat(LOCALE, {
  style: 'percent',
  maximumFractionDigits: 0,
});
const ratioPercentFormat = new Intl.NumberFormat(LOCALE, {
  style: 'percent',
  minimumFractionDigits: 2,
  maximumFractionDigits: 4,
});

export function formatBytes(bytes: number): string {
  let exponent = 0;
  while (exponent < BYTE_UNITS.length - 1 && bytes >= 1024 ** (exponent + 1)) {
    exponent += 1;
  }
  const format = exponent === 0 ? integerFormat : scaledBytesFormat;
  return `${format.format(bytes / 1024 ** exponent)}${NO_BREAK_SPACE}${BYTE_UNITS[exponent] ?? 'B'}`;
}

export function formatCount(value: number): string {
  return integerFormat.format(value);
}

export function formatDecimalCount(digits: string): string {
  return integerFormat.format(BigInt(digits));
}

export function formatPercent(percent: number): string {
  return wholePercentFormat.format(percent / 100);
}

export function formatRatioAsPercent(ratio: string | null): string {
  return ratio === null ? EMPTY_VALUE : ratioPercentFormat.format(Number(ratio));
}

export function formatDateTime(isoDateTime: string | null, timeZone?: string): string {
  if (isoDateTime === null) {
    return EMPTY_VALUE;
  }
  return new Intl.DateTimeFormat(LOCALE, {
    dateStyle: 'short',
    timeStyle: 'medium',
    timeZone,
  }).format(new Date(isoDateTime));
}
