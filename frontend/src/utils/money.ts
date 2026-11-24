const PT_BR_INTEGER = new Intl.NumberFormat('pt-BR', {
  maximumFractionDigits: 0,
});

const DECIMAL_INPUT_PATTERN = /^([+-])?(\d+)(?:[.,](\d{0,2}))?$/;

/**
 * Formats an integer amount in centavos into Brazilian Reais, e.g. `12345 -> "R$ 123,45"`.
 * Uses BigInt / integer quotient and remainder — never floating-point division.
 */
export function formatCents(cents: number): string {
  if (!Number.isFinite(cents) || !Number.isInteger(cents)) {
    throw new Error(`Amount in centavos must be a safe integer (received ${cents}).`);
  }

  const isNegative = cents < 0;
  const absCents = Math.abs(cents);
  const reais = Math.trunc(absCents / 100);
  const centavos = absCents % 100;

  const formattedReais = PT_BR_INTEGER.format(reais);
  const formattedCentavos = String(centavos).padStart(2, '0');
  const prefix = isNegative ? '-R$ ' : 'R$ ';

  return `${prefix}${formattedReais},${formattedCentavos}`;
}

/**
 * Formats an integer amount in centavos into a plain `pt-BR` editable input string
 * without the currency prefix, e.g. `150000 -> "1500,00"` or `12345 -> "123,45"`.
 */
export function formatCentsForInput(cents: number): string {
  if (!Number.isFinite(cents) || !Number.isInteger(cents)) {
    throw new Error(`Amount in centavos must be a safe integer (received ${cents}).`);
  }

  const isNegative = cents < 0;
  const absCents = Math.abs(cents);
  const reais = Math.trunc(absCents / 100);
  const centavos = absCents % 100;
  const sign = isNegative ? '-' : '';

  return `${sign}${reais},${String(centavos).padStart(2, '0')}`;
}

/**
 * Parses a user-entered Reais string (e.g. `"123,45"`, `"R$ 1.500,00"`, `"200"`, `"10.5"`)
 * into integer centavos (`12345`, `150000`, `20000`, `1050`) using string manipulation only.
 * Returns `null` if the string is empty or malformed.
 */
export function parseReaisToCents(input: string): number | null {
  const cleaned = stripCurrencyAndThousands(input);
  if (!cleaned) {
    return null;
  }

  const match = DECIMAL_INPUT_PATTERN.exec(cleaned);
  if (!match) {
    return null;
  }

  const [, sign, wholePart, fractionPart = ''] = match;
  const paddedFraction = fractionPart.padEnd(2, '0');

  const reais = Number.parseInt(wholePart, 10);
  const centavos = Number.parseInt(paddedFraction, 10);
  const totalCents = reais * 100 + centavos;

  if (!Number.isSafeInteger(totalCents)) {
    return null;
  }

  return sign === '-' && totalCents !== 0 ? -totalCents : totalCents;
}

function stripCurrencyAndThousands(raw: string): string {
  const withoutCurrency = raw.replace(/R\$/gi, '').trim();
  if (!withoutCurrency) {
    return '';
  }

  // If the string contains both dots and a comma (e.g. "1.500,50"), dots are thousands separators
  if (withoutCurrency.includes(',') && withoutCurrency.includes('.')) {
    const lastComma = withoutCurrency.lastIndexOf(',');
    const lastDot = withoutCurrency.lastIndexOf('.');
    if (lastComma > lastDot) {
      return withoutCurrency.replace(/\./g, '');
    }
    return withoutCurrency.replace(/,/g, '');
  }

  // If multiple dots exist (e.g. "1.500.000") or a dot is followed by exactly 3 digits ("1.500"), treat as thousands
  if (/^\d{1,3}(\.\d{3})+$/.test(withoutCurrency)) {
    return withoutCurrency.replace(/\./g, '');
  }

  return withoutCurrency;
}
