export const MAX_FILE_BYTES = 5 * 1024 ** 3;
export const MAX_FILE_NAME_LENGTH = 255;
export const EXPECTED_HEADER =
  'advertiserId;campaignId;creativeId;adBoxUUID;impressions;clicks;revenue;advertiserMargin';

const HEADER_SNIFF_BYTES = 4096;
const CSV_FILE_NAME = /^.+\.csv$/i;
const FORBIDDEN_NAME_CHARACTERS = /[/\\\p{C}]/u;

export async function validateCsvFile(file: File): Promise<string[]> {
  const issues: string[] = [];
  if (!CSV_FILE_NAME.test(file.name)) {
    issues.push('Plik musi mieć rozszerzenie .csv');
  }
  if (file.name.length > MAX_FILE_NAME_LENGTH) {
    issues.push('Nazwa pliku jest za długa (maks. 255 znaków)');
  }
  if (FORBIDDEN_NAME_CHARACTERS.test(file.name)) {
    issues.push('Nazwa pliku zawiera niedozwolone znaki (/, \\ lub znaki sterujące)');
  }
  if (file.size === 0) {
    issues.push('Plik jest pusty');
  }
  if (file.size > MAX_FILE_BYTES) {
    issues.push('Plik przekracza limit 5 GiB');
  }
  if (issues.length === 0 && (await readFirstLine(file)) !== EXPECTED_HEADER) {
    issues.push(
      'Pierwsza linia nie jest oczekiwanym nagłówkiem (8 kolumn rozdzielonych średnikiem)',
    );
  }
  return issues;
}

async function readFirstLine(file: File): Promise<string> {
  const head = await file.slice(0, HEADER_SNIFF_BYTES).text();
  return head.replace(/^\uFEFF/, '').split(/\r?\n/, 1)[0] ?? '';
}
