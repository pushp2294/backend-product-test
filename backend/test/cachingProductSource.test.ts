import { describe, expect, it, vi } from 'vitest';
import { CachingProductSource } from '../src/adapters/caching/CachingProductSource';
import { RecordingLogger } from './fakes/RecordingLogger';
import { UpstreamError } from '../src/domain/errors';
import type { Category, Product } from '../src/domain/types';
import type { ProductSource } from '../src/ports/ProductSource';

const TTL_MS = 60_000;
const MAX_STALE_MS = 10 * 60_000;

function product(id: string): Product {
  return {
    id,
    title: `Product ${id}`,
    description: '',
    price: { amountMinor: 100, currency: 'USD' },
    imageUrl: `https://img.test/${id}.jpg`,
    rating: { average: 4, count: 10 },
    category: { id: 'electronics', name: 'Electronics' },
  };
}

/** An inner ProductSource whose methods are spies, so tests can count real calls. */
function spySource() {
  return {
    listProducts: vi.fn(async (): Promise<Product[]> => [product('1'), product('2')]),
    getProduct: vi.fn(async (id: string): Promise<Product | null> => (id === '404' ? null : product(id))),
    listCategories: vi.fn(async (): Promise<Category[]> => [{ id: 'electronics', name: 'Electronics' }]),
  } satisfies ProductSource;
}

/** A clock the test controls: starts at 0, moves only when `advance` is called. */
function fakeClock() {
  let time = 0;
  return { now: () => time, advance: (ms: number) => (time += ms) };
}

describe('CachingProductSource', () => {
  it('serves a second call within the TTL from cache, without calling the inner source', async () => {
    const inner = spySource();
    const clock = fakeClock();
    const cache = new CachingProductSource(inner, TTL_MS, new RecordingLogger(), MAX_STALE_MS, clock.now);

    const first = await cache.listProducts();
    clock.advance(TTL_MS - 1);
    const second = await cache.listProducts();

    expect(second).toBe(first);
    expect(inner.listProducts).toHaveBeenCalledTimes(1);
  });

  it('calls the inner source again once the TTL has expired', async () => {
    const inner = spySource();
    const clock = fakeClock();
    const cache = new CachingProductSource(inner, TTL_MS, new RecordingLogger(), MAX_STALE_MS, clock.now);

    await cache.listProducts();
    clock.advance(TTL_MS);
    await cache.listProducts();

    expect(inner.listProducts).toHaveBeenCalledTimes(2);
  });

  it('propagates an inner error and does not cache it: the next call retries', async () => {
    const inner = spySource();
    inner.listCategories.mockRejectedValueOnce(new UpstreamError('down'));
    const cache = new CachingProductSource(inner, TTL_MS, new RecordingLogger(), MAX_STALE_MS, fakeClock().now);

    await expect(cache.listCategories()).rejects.toThrow(UpstreamError);
    const categories = await cache.listCategories();

    expect(categories).toEqual([{ id: 'electronics', name: 'Electronics' }]);
    expect(inner.listCategories).toHaveBeenCalledTimes(2);
  });

  it('caches getProduct separately per id', async () => {
    const inner = spySource();
    const cache = new CachingProductSource(inner, TTL_MS, new RecordingLogger(), MAX_STALE_MS, fakeClock().now);

    await cache.getProduct('1');
    await cache.getProduct('2');
    await cache.getProduct('1');
    await cache.getProduct('2');

    expect(inner.getProduct).toHaveBeenCalledTimes(2);
    expect(inner.getProduct).toHaveBeenNthCalledWith(1, '1');
    expect(inner.getProduct).toHaveBeenNthCalledWith(2, '2');
  });

  it('does not cache "not found", so made-up ids cannot grow the cache', async () => {
    const inner = spySource();
    const cache = new CachingProductSource(inner, TTL_MS, new RecordingLogger(), MAX_STALE_MS, fakeClock().now);

    expect(await cache.getProduct('404')).toBeNull();
    expect(await cache.getProduct('404')).toBeNull();

    expect(inner.getProduct).toHaveBeenCalledTimes(2);
  });

  it('keeps each method in its own cache entry', async () => {
    const inner = spySource();
    const cache = new CachingProductSource(inner, TTL_MS, new RecordingLogger(), MAX_STALE_MS, fakeClock().now);

    await cache.listProducts();
    await cache.listCategories();
    await cache.listProducts();
    await cache.listCategories();

    expect(inner.listProducts).toHaveBeenCalledTimes(1);
    expect(inner.listCategories).toHaveBeenCalledTimes(1);
  });

  describe('stale on error', () => {
    function setup() {
      const inner = spySource();
      const clock = fakeClock();
      const logger = new RecordingLogger();
      const cache = new CachingProductSource(inner, TTL_MS, logger, MAX_STALE_MS, clock.now);
      return { inner, clock, logger, cache };
    }

    it('serves the stale value with a warning when the source fails within the bound', async () => {
      const { inner, clock, logger, cache } = setup();
      const fresh = await cache.listProducts();

      clock.advance(5 * 60_000); // expired (TTL 1 min) but well within the 10 min bound
      inner.listProducts.mockRejectedValueOnce(new UpstreamError('Fake Store timed out'));
      const served = await cache.listProducts();

      expect(served).toBe(fresh);
      expect(inner.listProducts).toHaveBeenCalledTimes(2); // it DID try the source first
      expect(logger.warnings).toHaveLength(1);
      expect(logger.warnings[0]?.context).toMatchObject({ key: 'listProducts', ageMs: 5 * 60_000 });
    });

    it('lets the error through once the cached value is older than the bound', async () => {
      const { inner, clock, logger, cache } = setup();
      await cache.listProducts();

      clock.advance(MAX_STALE_MS + 1);
      inner.listProducts.mockRejectedValueOnce(new UpstreamError('Fake Store timed out'));

      await expect(cache.listProducts()).rejects.toThrow(UpstreamError);
      expect(logger.warnings).toEqual([]);
    });

    it('never masks an error that is not a source failure (a bug must stay loud)', async () => {
      const { inner, clock, cache } = setup();
      await cache.listProducts();

      clock.advance(TTL_MS + 1);
      inner.listProducts.mockRejectedValueOnce(new TypeError("Cannot read properties of undefined"));

      await expect(cache.listProducts()).rejects.toThrow(TypeError);
    });

    it('keeps retrying the source after serving stale, and takes fresh data as soon as it recovers', async () => {
      const { inner, clock, cache } = setup();
      await cache.listProducts();
      clock.advance(TTL_MS + 1);
      inner.listProducts.mockRejectedValueOnce(new UpstreamError('down'));
      await cache.listProducts(); // stale served

      inner.listProducts.mockResolvedValueOnce([product('99')]);
      const recovered = await cache.listProducts();

      expect(recovered.map((p) => p.id)).toEqual(['99']);
      expect(inner.listProducts).toHaveBeenCalledTimes(3);
    });

    it('has nothing to serve on a cold cache, so the first failure propagates', async () => {
      const { inner, cache } = setup();
      inner.listProducts.mockRejectedValueOnce(new UpstreamError('down'));

      await expect(cache.listProducts()).rejects.toThrow(UpstreamError);
    });
  });
});
