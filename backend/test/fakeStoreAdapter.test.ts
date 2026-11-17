import { describe, expect, it } from 'vitest';
import { FakeStoreProductSource } from '../src/adapters/fakestore/FakeStoreProductSource';
import { UpstreamError } from '../src/domain/errors';
import { fixture, hangingFetch, stubFetch } from './fakes/stubFetch';

const BASE_URL = 'https://fakestore.test';
const productsJson = fixture('fakestore-products.json');
const categoriesJson = fixture('fakestore-categories.json');

/** The raw JSON for one product from the live snapshot, as Fake Store would serve it alone. */
function rawProductJson(id: number): string {
  const all = JSON.parse(productsJson) as Array<{ id: number }>;
  return JSON.stringify(all.find((p) => p.id === id));
}

function sourceWith(routes: Parameters<typeof stubFetch>[0]) {
  const stub = stubFetch(routes);
  return { source: new FakeStoreProductSource({ baseUrl: BASE_URL, fetchFn: stub.fetchFn }), calls: stub.calls };
}

describe('FakeStoreProductSource: mapping to domain types', () => {
  it('maps a live-snapshot product to exactly our domain shape', async () => {
    const { source } = sourceWith({ '/products': { status: 200, body: productsJson } });

    const products = await source.listProducts();

    expect(products).toHaveLength(20);
    // toStrictEqual fails on any extra key, so no Fake Store field (image, rate, ...) can leak through.
    expect(products[0]).toStrictEqual({
      id: '1',
      title: 'Fjallraven - Foldsack No. 1 Backpack, Fits 15 Laptops',
      description: expect.any(String),
      price: { amountMinor: 10995, currency: 'USD' },
      imageUrl: 'https://fakestoreapi.com/img/81fPKd-2AYL._AC_SL1500_t.png',
      rating: { average: 3.9, count: 120 },
      category: { id: 'mens-clothing', name: "Men's clothing" },
    });
  });

  it('turns every numeric Fake Store id into a string', async () => {
    const { source } = sourceWith({ '/products': { status: 200, body: productsJson } });

    const products = await source.listProducts();

    for (const product of products) {
      expect(typeof product.id).toBe('string');
    }
    expect(products.map((p) => p.id)).toEqual(Array.from({ length: 20 }, (_, i) => String(i + 1)));
  });

  it('rounds float noise when converting to minor units (1.15 * 100 is 114.999... in JS)', async () => {
    // No live price shows float noise, so this input is crafted: product 1 re-priced to 1.15.
    const repriced = productsJson.replace('"price":109.95', '"price":1.15');
    const { source } = sourceWith({ '/products': { status: 200, body: repriced } });

    const [first] = await source.listProducts();

    expect(first?.price.amountMinor).toBe(115);
  });

  it('maps categories to our own slugs and capitalised names', async () => {
    const { source } = sourceWith({ '/products/categories': { status: 200, body: categoriesJson } });

    expect(await source.listCategories()).toStrictEqual([
      { id: 'electronics', name: 'Electronics' },
      { id: 'jewelery', name: 'Jewelery' },
      { id: 'mens-clothing', name: "Men's clothing" },
      { id: 'womens-clothing', name: "Women's clothing" },
    ]);
  });

  it('tolerates Fake Store ADDING a field: unknown keys are ignored, not a 502', async () => {
    const withExtra = productsJson.replace('"id":1,', '"id":1,"stock":42,');
    const { source } = sourceWith({ '/products': { status: 200, body: withExtra } });

    const [first] = await source.listProducts();

    expect(first?.id).toBe('1');
    expect(first).not.toHaveProperty('stock');
  });

  it('still rejects a MISSING required field', async () => {
    const missingTitle = productsJson.replace('"title":"Fjallraven - Foldsack No. 1 Backpack, Fits 15 Laptops",', '');
    const { source } = sourceWith({ '/products': { status: 200, body: missingTitle } });

    await expect(source.listProducts()).rejects.toThrow(/unexpected shape/);
  });
});

describe('FakeStoreProductSource: getProduct', () => {
  it('returns the mapped product for an existing id', async () => {
    const { source, calls } = sourceWith({ '/products/12': { status: 200, body: rawProductJson(12) } });

    const product = await source.getProduct('12');

    expect(product?.id).toBe('12');
    expect(product?.rating).toEqual({ average: 4.8, count: 400 });
    expect(calls).toEqual([`${BASE_URL}/products/12`]);
  });

  it('returns null when Fake Store answers 200 with an empty body (observed quirk)', async () => {
    const { source } = sourceWith({ '/products/999': { status: 200, body: '' } });

    expect(await source.getProduct('999')).toBeNull();
  });

  it('returns null for a real 404, in case Fake Store ever fixes the quirk', async () => {
    const { source } = sourceWith({ '/products/999': { status: 404, body: 'Not Found' } });

    expect(await source.getProduct('999')).toBeNull();
  });

  it.each(['abc', '0', '-1', '1.5', '../users', ''])(
    'returns null for the impossible id %j without calling the network',
    async (id) => {
      const { source, calls } = sourceWith({});

      expect(await source.getProduct(id)).toBeNull();
      expect(calls).toEqual([]);
    },
  );
});

describe('FakeStoreProductSource: upstream failures become UpstreamError', () => {
  it('times out a hung upstream as UpstreamError instead of hanging', async () => {
    const source = new FakeStoreProductSource({ baseUrl: BASE_URL, fetchFn: hangingFetch, timeoutMs: 20 });

    const error = await source.listProducts().catch((e: unknown) => e);

    expect(error).toBeInstanceOf(UpstreamError);
    expect((error as Error).message).toMatch(/timed out after 20ms/);
  });

  it('wraps a network error as UpstreamError', async () => {
    const fetchFn = (async () => {
      throw new TypeError('fetch failed');
    }) as typeof fetch;
    const source = new FakeStoreProductSource({ baseUrl: BASE_URL, fetchFn });

    await expect(source.listProducts()).rejects.toThrow(UpstreamError);
  });

  it('wraps an HTTP 5xx as UpstreamError', async () => {
    const { source } = sourceWith({ '/products': { status: 503, body: 'down' } });

    await expect(source.listProducts()).rejects.toThrow(/HTTP 503/);
  });

  it('wraps invalid JSON as UpstreamError', async () => {
    const { source } = sourceWith({ '/products': { status: 200, body: '<html>oops</html>' } });

    await expect(source.listProducts()).rejects.toThrow(UpstreamError);
  });

  it('wraps an unexpected shape (e.g. id became a string) as UpstreamError', async () => {
    const changed = productsJson.replace('"id":1,', '"id":"1",');
    const { source } = sourceWith({ '/products': { status: 200, body: changed } });

    await expect(source.listProducts()).rejects.toThrow(/unexpected shape/);
  });
});
