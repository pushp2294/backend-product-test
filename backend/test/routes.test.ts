import request from 'supertest';
import { beforeEach, describe, expect, it } from 'vitest';
import { FakeStoreProductSource } from '../src/adapters/fakestore/FakeStoreProductSource';
import { API_PREFIX, buildApp } from '../src/app';
import { CatalogueService } from '../src/domain/catalogue';
import { EnquiryService } from '../src/domain/enquiries';
import type { Category, Product } from '../src/domain/types';
import type { ProductSource } from '../src/ports/ProductSource';
import { liveSnapshotSource } from './fakes/InMemoryProductSource';
import { RecordingEmailSender } from './fakes/RecordingEmailSender';
import { RecordingLogger } from './fakes/RecordingLogger';
import { hangingFetch, stubFetch } from './fakes/stubFetch';

let logger: RecordingLogger;

beforeEach(() => {
  logger = new RecordingLogger();
});

/** App over the live snapshot (the normal case for route tests). */
async function snapshotApp() {
  return appWith(await liveSnapshotSource());
}

/** App over any ProductSource, e.g. the real Fake Store adapter with a stubbed network. */
function appWith(source: ProductSource) {
  const enquiries = new EnquiryService(source, new RecordingEmailSender(), { salesInbox: 's@x.test', from: 'f@x.test' });
  return buildApp({ catalogue: new CatalogueService(source, logger), enquiries, logger });
}

const api = (path: string) => `${API_PREFIX}${path}`;
const ids = (items: Array<{ id: string }>) => items.map((p) => p.id);

describe('GET /health', () => {
  it('returns ok without touching the product source', async () => {
    const res = await request(appWith(failingSource(new Error('must not be called')))).get(api('/health'));

    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: 'ok' });
  });
});

describe('GET /products/featured', () => {
  it('returns 5 product summaries in our contract shape', async () => {
    const res = await request(await snapshotApp()).get(api('/products/featured'));

    expect(res.status).toBe(200);
    expect(ids(res.body.products)).toEqual(['12', '11', '3', '18', '5']);
    // Exact keys: a summary has no description, and nothing from Fake Store leaks.
    expect(res.body.products[0]).toStrictEqual({
      id: '12',
      title: expect.any(String),
      price: { amountMinor: 11400, currency: 'USD' },
      imageUrl: expect.stringMatching(/^https:\/\//),
      rating: { average: 4.8, count: 400 },
      categoryId: 'electronics',
    });
  });
});

describe('GET /categories', () => {
  it('returns every category with our slug ids', async () => {
    const res = await request(await snapshotApp()).get(api('/categories'));

    expect(res.status).toBe(200);
    expect(ids(res.body.categories)).toEqual(['electronics', 'jewelery', 'mens-clothing', 'womens-clothing']);
  });
});

describe('GET /categories/:categoryId/products', () => {
  it('returns the category, its 2 top-rated products, and the others', async () => {
    const res = await request(await snapshotApp()).get(api('/categories/electronics/products'));

    expect(res.status).toBe(200);
    expect(res.body.category).toEqual({ id: 'electronics', name: 'Electronics' });
    expect(ids(res.body.topRated)).toEqual(['12', '11']);
    expect(ids(res.body.others)).toEqual(['9', '10', '13', '14']);
  });

  it('returns 404 NOT_FOUND in the standard error shape for an unknown category', async () => {
    const res = await request(await snapshotApp()).get(api('/categories/hats/products'));

    expect(res.status).toBe(404);
    expect(res.body).toEqual({ error: { code: 'NOT_FOUND', message: 'The category was not found' } });
  });
});

describe('GET /search', () => {
  it('returns matching products and echoes the trimmed query', async () => {
    const res = await request(await snapshotApp()).get(api('/search')).query({ q: '  men ' });

    expect(res.status).toBe(200);
    expect(res.body.query).toBe('men');
    expect(ids(res.body.products)).toEqual(['3', '2', '4']);
  });

  it('returns 200 with an empty list when nothing matches', async () => {
    const res = await request(await snapshotApp()).get(api('/search')).query({ q: 'submarine' });

    expect(res.status).toBe(200);
    expect(res.body.products).toEqual([]);
  });

  it.each([
    ['missing', undefined],
    ['blank', '   '],
    ['over 100 characters', 'a'.repeat(101)],
  ])('returns 400 VALIDATION_ERROR naming the field when q is %s', async (_label, q) => {
    const res = await request(await snapshotApp()).get(api('/search')).query(q === undefined ? {} : { q });

    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
    expect(res.body.error.details[0].field).toBe('q');
  });

  it('returns 400 when q is repeated (?q=a&q=b arrives as an array)', async () => {
    const res = await request(await snapshotApp()).get(api('/search?q=a&q=b'));

    expect(res.status).toBe(400);
  });
});

describe('GET /products/:id', () => {
  it('returns the product detail, which adds description and category to the summary', async () => {
    const res = await request(await snapshotApp()).get(api('/products/12'));

    expect(res.status).toBe(200);
    expect(res.body.product).toMatchObject({
      id: '12',
      categoryId: 'electronics',
      category: { id: 'electronics', name: 'Electronics' },
      description: expect.any(String),
    });
  });

  it('returns 404 NOT_FOUND for an unknown id', async () => {
    const res = await request(await snapshotApp()).get(api('/products/999'));

    expect(res.status).toBe(404);
    expect(res.body).toEqual({ error: { code: 'NOT_FOUND', message: 'The product was not found' } });
  });

  it('returns 404 (not a crash, not a 200) when Fake Store answers 200 with an empty body', async () => {
    // Real adapter, stubbed network: exercises the actual quirk end to end.
    const { fetchFn } = stubFetch({ '/products/999': { status: 200, body: '' } });
    const app = appWith(new FakeStoreProductSource({ baseUrl: 'https://fakestore.test', fetchFn }));

    const res = await request(app).get(api('/products/999'));

    expect(res.status).toBe(404);
    expect(res.body.error.code).toBe('NOT_FOUND');
  });
});

describe('upstream failures', () => {
  it('returns 502 UPSTREAM_UNAVAILABLE when the product source hangs past its timeout', async () => {
    // Real adapter over a fetch that never answers; a 50ms timeout keeps the test fast.
    const app = appWith(
      new FakeStoreProductSource({ baseUrl: 'https://fakestore.test', fetchFn: hangingFetch, timeoutMs: 50 }),
    );

    const res = await request(app).get(api('/products/featured'));

    expect(res.status).toBe(502);
    expect(res.body).toEqual({
      error: { code: 'UPSTREAM_UNAVAILABLE', message: 'Product data is temporarily unavailable' },
    });
    // The client never learns which upstream we use; the detail goes to the log only.
    expect(JSON.stringify(res.body)).not.toMatch(/fake ?store|timed out/i);
    expect(logger.warnings[0]?.context?.error).toMatch(/timed out after 50ms/);
  });
});

describe('errors that are not the client\'s fault and not upstream', () => {
  it('returns 500 INTERNAL_ERROR with a generic message and no internals for an unexpected bug', async () => {
    const res = await request(appWith(failingSource(new TypeError("Cannot read properties of undefined (reading 'x')")))).get(
      api('/products/featured'),
    );

    expect(res.status).toBe(500);
    expect(res.body).toEqual({ error: { code: 'INTERNAL_ERROR', message: 'Something went wrong' } });
    expect(logger.errors[0]?.context?.error).toMatch(/TypeError/);
    expect(logger.warnings).toEqual([]);
  });
});

describe('framework-level behaviour', () => {
  it('returns 404 NOT_FOUND in the standard shape for an unknown route', async () => {
    const res = await request(await snapshotApp()).get(api('/nope'));

    expect(res.status).toBe(404);
    expect(res.body).toEqual({ error: { code: 'NOT_FOUND', message: 'Resource not found' } });
  });

  it('returns 400 VALIDATION_ERROR for a malformed JSON body', async () => {
    const res = await request(await snapshotApp())
      .post(api('/anything'))
      .set('Content-Type', 'application/json')
      .send('{"name": ');

    expect(res.status).toBe(400);
    expect(res.body).toEqual({ error: { code: 'VALIDATION_ERROR', message: 'Request body is not valid JSON' } });
  });

  it('returns 413 PAYLOAD_TOO_LARGE for a body over 10kb', async () => {
    const res = await request(await snapshotApp())
      .post(api('/anything'))
      .send({ message: 'x'.repeat(11 * 1024) });

    expect(res.status).toBe(413);
    expect(res.body.error.code).toBe('PAYLOAD_TOO_LARGE');
  });

  it('does not send an X-Powered-By header', async () => {
    const res = await request(await snapshotApp()).get(api('/health'));

    expect(res.headers['x-powered-by']).toBeUndefined();
  });
});

/** A ProductSource where every method rejects with the given error. */
function failingSource(error: Error): ProductSource {
  const fail = async (): Promise<never> => {
    throw error;
  };
  return {
    listProducts: fail as () => Promise<Product[]>,
    getProduct: fail as () => Promise<Product | null>,
    listCategories: fail as () => Promise<Category[]>,
  };
}
