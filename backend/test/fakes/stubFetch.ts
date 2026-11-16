// Test helpers that stand in for the network when testing FakeStoreProductSource.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

export type StubResponse = { status: number; body: string };

/**
 * Raw text of a snapshot file, exactly as captured from the live API. The same files are
 * served in production by SnapshotProductSource (PRODUCT_SOURCE=snapshot): one copy.
 */
export function fixture(name: string): string {
  return readFileSync(join(__dirname, '..', '..', 'snapshot', name), 'utf8');
}

/**
 * A fetch replacement that answers by URL path and records every URL requested.
 * Unknown paths throw, so a test fails loudly if the adapter calls something unexpected.
 */
export function stubFetch(routes: Record<string, StubResponse>) {
  const calls: string[] = [];
  const fetchFn = (async (input: string | URL | Request) => {
    const url = String(input);
    calls.push(url);
    const route = routes[new URL(url).pathname];
    if (!route) {
      throw new Error(`stubFetch: no route for ${url}`);
    }
    return new Response(route.body, { status: route.status });
  }) as typeof fetch;
  return { fetchFn, calls };
}

/** A fetch that never answers, and only settles when the request's abort signal fires. */
export const hangingFetch = ((_input: string | URL | Request, init?: RequestInit) =>
  new Promise<Response>((_resolve, reject) => {
    const signal = init?.signal;
    if (!signal) {
      // Without a signal this would hang the test run, so fail fast instead.
      reject(new Error('hangingFetch: request was made without an abort signal'));
      return;
    }
    signal.addEventListener('abort', () => reject(signal.reason));
  })) as typeof fetch;
