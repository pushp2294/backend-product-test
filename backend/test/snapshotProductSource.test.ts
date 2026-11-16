import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createProductSource } from '../src/adapters/createProductSource';
import { FakeStoreProductSource } from '../src/adapters/fakestore/FakeStoreProductSource';
import { SnapshotProductSource } from '../src/adapters/fakestore/SnapshotProductSource';
import { loadConfig } from '../src/config';
import { UpstreamError } from '../src/domain/errors';
import { fixture, stubFetch } from './fakes/stubFetch';

// backend/snapshot, the same folder app.ts serves when PRODUCT_SOURCE=snapshot.
const SNAPSHOT_DIRECTORY = join(__dirname, '..', 'snapshot');

describe('SnapshotProductSource (the real backend/snapshot files)', () => {
  const source = new SnapshotProductSource(SNAPSHOT_DIRECTORY);

  it('maps the snapshot to our domain types: string ids, minor units, slug categories', async () => {
    const products = await source.listProducts();

    expect(products).toHaveLength(20);
    expect(products[0]).toMatchObject({
      id: '1',
      price: { amountMinor: 10995, currency: 'USD' },
      category: { id: 'mens-clothing', name: "Men's clothing" },
    });
    expect(products.every((p) => typeof p.id === 'string')).toBe(true);
  });

  it('produces EXACTLY what the live adapter produces from the same data (one shared mapper)', async () => {
    const { fetchFn } = stubFetch({
      '/products': { status: 200, body: fixture('fakestore-products.json') },
      '/products/categories': { status: 200, body: fixture('fakestore-categories.json') },
    });
    const live = new FakeStoreProductSource({ baseUrl: 'https://fakestore.test', fetchFn });

    expect(await source.listProducts()).toEqual(await live.listProducts());
    expect(await source.listCategories()).toEqual(await live.listCategories());
  });

  it('returns one product by id, and null for an id that is not in the snapshot', async () => {
    expect((await source.getProduct('12'))?.title).toContain('WD 4TB');
    expect(await source.getProduct('999')).toBeNull();
  });

  it('lists the 4 categories', async () => {
    expect((await source.listCategories()).map((c) => c.id)).toEqual([
      'electronics',
      'jewelery',
      'mens-clothing',
      'womens-clothing',
    ]);
  });
});

describe('SnapshotProductSource failures become UpstreamError (502), like the live source', () => {
  let dir: string;

  beforeEach(async () => {
    dir = await mkdtemp(join(tmpdir(), 'snapshot-'));
  });

  afterEach(async () => {
    await rm(dir, { recursive: true, force: true });
  });

  it('a missing snapshot file', async () => {
    await expect(new SnapshotProductSource(dir).listProducts()).rejects.toThrow(UpstreamError);
  });

  it('a snapshot file that is not valid JSON', async () => {
    await writeFile(join(dir, 'fakestore-products.json'), '{ not json');

    await expect(new SnapshotProductSource(dir).listProducts()).rejects.toThrow(/invalid JSON/);
  });

  it('a snapshot file with the wrong shape', async () => {
    await writeFile(join(dir, 'fakestore-products.json'), JSON.stringify([{ id: 'one' }]));

    await expect(new SnapshotProductSource(dir).listProducts()).rejects.toThrow(/unexpected shape/);
  });
});

describe('createProductSource: one config value picks the data source', () => {
  it('PRODUCT_SOURCE unset (default) gives the live Fake Store source', () => {
    expect(createProductSource(loadConfig({}), SNAPSHOT_DIRECTORY)).toBeInstanceOf(FakeStoreProductSource);
  });

  it('PRODUCT_SOURCE=snapshot gives the snapshot source', () => {
    expect(createProductSource(loadConfig({ PRODUCT_SOURCE: 'snapshot' }), SNAPSHOT_DIRECTORY)).toBeInstanceOf(
      SnapshotProductSource,
    );
  });
});
