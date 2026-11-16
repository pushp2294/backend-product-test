import { UpstreamError } from '../../domain/errors';
import type { Category, Product } from '../../domain/types';
import type { ProductSource } from '../../ports/ProductSource';
import { decodeCategories, decodeProduct, decodeProducts } from './fakeStoreMapping';

const DEFAULT_TIMEOUT_MS = 5_000;

export interface FakeStoreOptions {
  baseUrl: string;
  /** Per-request timeout. Defaults to 5s; tests pass a tiny value to avoid waiting. */
  timeoutMs?: number;
  // The fetch function is injected so tests can stub the network without patching globals.
  fetchFn?: typeof fetch;
}

/**
 * ProductSource backed by the live Fake Store API. Everything about talking to it over
 * HTTP (URLs, timeout, the empty-body quirk) stays in this file; turning its JSON into
 * our domain types is shared with SnapshotProductSource in fakeStoreMapping.ts.
 */
export class FakeStoreProductSource implements ProductSource {
  private readonly baseUrl: string;
  private readonly timeoutMs: number;
  private readonly fetchFn: typeof fetch;

  constructor(options: FakeStoreOptions) {
    // Strip a trailing slash so `${baseUrl}/products` never becomes "//products".
    this.baseUrl = options.baseUrl.replace(/\/+$/, '');
    this.timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
    this.fetchFn = options.fetchFn ?? fetch;
  }

  async listProducts(): Promise<Product[]> {
    return decodeProducts((await this.request('/products')) ?? '', '/products');
  }

  async getProduct(id: string): Promise<Product | null> {
    // Fake Store ids are positive integers. Anything else cannot exist, so we answer
    // without a network call, and untrusted input never reaches the upstream URL.
    if (!/^[1-9]\d*$/.test(id)) {
      return null;
    }

    const body = await this.request(`/products/${id}`, { notFoundIsNull: true });

    // Quirk (observed 2026-11-16): a missing product returns HTTP 200 with an EMPTY
    // body, not 404. We translate it to the port's "not found" here so it never
    // escapes this adapter. `null` also covers a real 404 if they ever fix it.
    if (body === null || body.trim() === '') {
      return null;
    }

    return decodeProduct(body, `/products/${id}`);
  }

  async listCategories(): Promise<Category[]> {
    return decodeCategories((await this.request('/products/categories')) ?? '', '/products/categories');
  }

  /**
   * GET a path and return the raw body text. Returns `null` for a 404 only when
   * asked to. Any other failure becomes UpstreamError (which the HTTP layer maps to 502).
   */
  private async request(path: string, options?: { notFoundIsNull: true }): Promise<string | null> {
    const url = `${this.baseUrl}${path}`;
    let response: Response;
    try {
      // AbortSignal.timeout aborts the request after timeoutMs, so a hung upstream
      // cannot hang our server: the await below rejects instead of waiting forever.
      response = await this.fetchFn(url, { signal: AbortSignal.timeout(this.timeoutMs) });
    } catch (error) {
      // AbortSignal.timeout rejects with a DOMException named "TimeoutError".
      const timedOut = error instanceof Error && error.name === 'TimeoutError';
      throw new UpstreamError(
        timedOut
          ? `Product source timed out after ${this.timeoutMs}ms: ${path}`
          : `Product source unreachable: ${path}`,
        { cause: error },
      );
    }

    if (response.status === 404 && options?.notFoundIsNull) {
      return null;
    }
    if (!response.ok) {
      throw new UpstreamError(`Product source returned HTTP ${response.status}: ${path}`);
    }
    return response.text();
  }
}
