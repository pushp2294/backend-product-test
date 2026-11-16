// Guards the boundary rule "Fake Store response shapes never escape the adapter".
// A comment can state the rule; this test enforces it.
import { readFileSync, readdirSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';

const SRC = join(__dirname, '..', 'src');
const ADAPTER_DIR = join('adapters', 'fakestore');

function sourceFiles(): string[] {
  // `recursive` lists files in every subfolder (Node 20+).
  return readdirSync(SRC, { recursive: true, encoding: 'utf8' }).filter((f) => f.endsWith('.ts'));
}

describe('architecture boundaries', () => {
  // Matches any quoted module path ending in /fakeStoreSchemas: `from '...'`, a bare
  // side-effect `import '...'`, or `require('...')`. An unquoted mention in a comment is not a violation.
  it('imports fakeStoreSchemas only from inside adapters/fakestore', () => {
    const offenders = sourceFiles()
      .filter((file) => !file.startsWith(ADAPTER_DIR))
      .filter((file) => /['"][^'"]*\/fakeStoreSchemas['"]/.test(readFileSync(join(SRC, file), 'utf8')))
      .map((file) => relative(SRC, join(SRC, file)));

    expect(offenders).toEqual([]);
  });

  it('keeps the domain free of adapter and HTTP imports', () => {
    const offenders = sourceFiles()
      .filter((file) => file.startsWith('domain'))
      // Matches any depth ('../adapters', '../../http', ...) in case domain grows subfolders.
      .filter((file) => /from '(\.\.\/)+(adapters|http)\//.test(readFileSync(join(SRC, file), 'utf8')));

    expect(offenders).toEqual([]);
  });
});
