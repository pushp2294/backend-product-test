import { UpstreamError } from '../../domain/errors';
import type { Category, Product } from '../../domain/types';
import type { Logger } from '../../ports/Logger';
import type { ProductSource } from '../../ports/ProductSource';

interface CacheEntry {
  value: unknown;
  /** When this value was fetched from the inner source (from the injected clock). */
  storedAt: number;
}

/** How old a cached value may be and still be served when the source is failing. */
export const DEFAULT_MAX_STALE_MS = 10 * 60_000;

/**
 * Decorator: a ProductSource that wraps another ProductSource and caches its
 * successful results in memory.
 *
 * This is why the port exists. Caching is added without touching any adapter or the
 * domain, and it wraps whichever source PRODUCT_SOURCE selects, unchanged.
 *
 * Two rules:
 * - FRESH: a value younger than `ttlMs` is served without calling the source.
 * - STALE ON ERROR: once a value is older than `ttlMs`, the source is called again. If
 *   that call fails with UpstreamError and the old value is younger than `maxStaleMs`,
 *   the old value is served and a warning is logged. Beyond `maxStaleMs` the error goes
 *   through: very old data is worse than an honest "Something went wrong."
 *
 * Deliberate limits (also in DECISIONS.md):
 * - Only UpstreamError is masked. Any other error is a bug and always propagates.
 * - Errors are never cached; the next request retries the source.
 * - `null` ("not found") is not cached, so requests for made-up ids cannot grow memory.
 * - Concurrent requests on a cold cache each reach the source (no de-duplication).
 * - Cached arrays are shared between callers, so callers must not mutate them.
 */
export class CachingProductSource implements ProductSource {
  private readonly entries = new Map<string, CacheEntry>();

  constructor(
    private readonly inner: ProductSource,
    private readonly ttlMs: number,
    private readonly logger: Logger,
    private readonly maxStaleMs: number = DEFAULT_MAX_STALE_MS,
    // The clock is injected so tests can move time forward instead of sleeping.
    private readonly now: () => number = Date.now,
  ) {}

  listProducts(): Promise<Product[]> {
    return this.cached('listProducts', () => this.inner.listProducts());
  }

  getProduct(id: string): Promise<Product | null> {
    return this.cached(`getProduct:${id}`, () => this.inner.getProduct(id));
  }

  listCategories(): Promise<Category[]> {
    return this.cached('listCategories', () => this.inner.listCategories());
  }

  private async cached<T>(key: string, load: () => Promise<T>): Promise<T> {
    const entry = this.entries.get(key);
    // Safe cast throughout: an entry under this key was stored by this same method, with this same T.
    if (entry && this.ageOf(entry) < this.ttlMs) {
      return entry.value as T;
    }

    let value: T;
    try {
      value = await load();
    } catch (error) {
      // Serve the old value only for a source failure, and only if it is not too old.
      if (entry && error instanceof UpstreamError && this.ageOf(entry) <= this.maxStaleMs) {
        this.logger.warn('Product source failing; serving stale cached data', {
          key,
          ageMs: this.ageOf(entry),
          error: error.message,
        });
        return entry.value as T;
      }
      throw error;
    }

    if (value !== null) {
      this.entries.set(key, { value, storedAt: this.now() });
    }
    return value;
  }

  private ageOf(entry: CacheEntry): number {
    return this.now() - entry.storedAt;
  }
}
