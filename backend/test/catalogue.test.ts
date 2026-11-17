import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CatalogueService, rankTopRated } from '../src/domain/catalogue';
import { NotFoundError } from '../src/domain/errors';
import type { Category, Product } from '../src/domain/types';
import type { ProductSource } from '../src/ports/ProductSource';
import { InMemoryProductSource, liveSnapshotSource } from './fakes/InMemoryProductSource';
import { RecordingLogger } from './fakes/RecordingLogger';

const ids = (products: Product[]) => products.map((p) => p.id);

let source: InMemoryProductSource;
let logger: RecordingLogger;

beforeEach(async () => {
  source = await liveSnapshotSource();
  logger = new RecordingLogger();
});

function catalogue(featuredProductIds?: string[]) {
  return new CatalogueService(source, logger, { featuredProductIds });
}

describe('featured products (home page)', () => {
  it('returns exactly [12, 11, 3, 18, 5] from live-shaped data: rate desc, then count desc', async () => {
    // 12 and 11 both rate 4.8 (count 400 vs 319); 3 and 18 both rate 4.7 (500 vs 130).
    expect(ids(await catalogue().getFeatured())).toEqual(['12', '11', '3', '18', '5']);
  });

  it('curated [1, 2, 999]: 5 products, includes 1 and 2, excludes 999, warns naming 999', async () => {
    const featured = await catalogue(['1', '2', '999']).getFeatured();

    expect(featured).toHaveLength(5);
    expect(ids(featured)).toContain('1');
    expect(ids(featured)).toContain('2');
    expect(ids(featured)).not.toContain('999');
    // Curated first in config order, then the rule fills the remaining 3 slots.
    expect(ids(featured)).toEqual(['1', '2', '12', '11', '3']);

    expect(logger.warnings).toHaveLength(1);
    expect(logger.warnings[0]?.message).toContain('999');
    expect(logger.warnings[0]?.context).toEqual({ unknownIds: ['999'] });
  });

  it('warns about an unknown curated id once per process, not on every request', async () => {
    const service = catalogue(['1', '999']);

    await service.getFeatured();
    await service.getFeatured();
    const third = await service.getFeatured();

    expect(third).toHaveLength(5);
    expect(logger.warnings).toHaveLength(1);
  });

  it('five valid curated ids: exactly those, in config order, with no warning', async () => {
    const featured = await catalogue(['20', '1', '9', '5', '14']).getFeatured();

    expect(ids(featured)).toEqual(['20', '1', '9', '5', '14']);
    expect(logger.warnings).toEqual([]);
  });

  it('fill skips products already curated, so nothing appears twice', async () => {
    // 3 is also third by the rule; it must not be repeated in the fill.
    const featured = await catalogue(['3']).getFeatured();

    expect(ids(featured)).toEqual(['3', '12', '11', '18', '5']);
  });

  it('does not mutate the source list (it may be a shared cached array)', async () => {
    const before = ids(await source.listProducts());

    await catalogue().getFeatured();

    expect(ids(await source.listProducts())).toEqual(before);
  });
});

describe('top-rated rule', () => {
  it('breaks a full tie (same rate and count) by id in numeric order, so "9" comes before "10"', () => {
    const base = { title: '', description: '', price: { amountMinor: 1, currency: 'USD' }, imageUrl: '' };
    const category = { id: 'c', name: 'C' };
    const tied: Product[] = ['10', '9'].map((id) => ({ ...base, id, category, rating: { average: 4, count: 10 } }));

    expect(ids(rankTopRated(tied))).toEqual(['9', '10']);
  });
});

describe('category page', () => {
  it('electronics (6 products): 2 top rated, then the other 4 by the same rule, no overlap', async () => {
    const result = await catalogue().getCategoryProducts('electronics');

    expect(result.category).toEqual({ id: 'electronics', name: 'Electronics' });
    expect(ids(result.topRated)).toEqual(['12', '11']);
    expect(ids(result.others)).toEqual(['9', '10', '13', '14']);
  });

  it("jewelery (4 products): 2 top rated, 2 others", async () => {
    const result = await catalogue().getCategoryProducts('jewelery');

    expect(ids(result.topRated)).toEqual(['5', '6']);
    expect(ids(result.others)).toEqual(['7', '8']);
  });

  it('ignores the curated home-page list: category pages always use the rule', async () => {
    const result = await catalogue(['10', '13']).getCategoryProducts('electronics');

    expect(ids(result.topRated)).toEqual(['12', '11']);
  });

  it('requests categories and products CONCURRENTLY, so the worst case is one upstream timeout, not two', async () => {
    // Neither upstream call resolves until the test releases it. If the code awaited
    // categories before asking for products, listProducts would not have been called yet.
    let releaseCategories!: (value: Category[]) => void;
    let releaseProducts!: (value: Product[]) => void;
    const slowSource: ProductSource = {
      listCategories: vi.fn(() => new Promise<Category[]>((resolve) => (releaseCategories = resolve))),
      listProducts: vi.fn(() => new Promise<Product[]>((resolve) => (releaseProducts = resolve))),
      getProduct: vi.fn(),
    };

    const pending = new CatalogueService(slowSource, logger).getCategoryProducts('electronics');
    await Promise.resolve(); // let the method run up to its first await

    expect(slowSource.listCategories).toHaveBeenCalledTimes(1);
    expect(slowSource.listProducts).toHaveBeenCalledTimes(1);

    releaseCategories(await source.listCategories());
    releaseProducts(await source.listProducts());
    expect(ids((await pending).topRated)).toEqual(['12', '11']);
  });

  it('throws NotFoundError for an unknown category', async () => {
    await expect(catalogue().getCategoryProducts('hats')).rejects.toThrow(NotFoundError);
  });
});

describe('search', () => {
  it('matches the title', async () => {
    expect(ids(await catalogue().search('backpack'))).toEqual(['1']);
  });

  it('matches the description, and orders results by the top-rated rule', async () => {
    // "fabric" appears in no title, only in descriptions of 2, 15, 18, 19, 20.
    expect(ids(await catalogue().search('fabric'))).toEqual(['18', '19', '2', '20', '15']);
  });

  it('matches the start of a word: "back" finds "backpack"', async () => {
    expect(ids(await catalogue().search('back'))).toEqual(['1']);
  });

  it('"men" finds men\'s clothing and NOT women\'s clothing', async () => {
    const results = await catalogue().search('men');

    // 2, 3, 4 mention "Men"/"Mens". Product 1 (backpack) is in men's clothing but its
    // text never says "men", and search does not look at the category.
    expect(ids(results)).toEqual(['3', '2', '4']);
    expect(results.every((p) => p.category.id === 'mens-clothing')).toBe(true);
  });

  it('treats regex characters as literal text: ".*" matches nothing (unescaped it would match all 20)', async () => {
    expect(await catalogue().search('.*')).toEqual([]);
  });

  it('treats "(" as a literal: no throw, and only the product whose text contains "(" ', async () => {
    // Unescaped, "\\b(" is an invalid regex and would throw. Product 16's description
    // literally contains "POLYURETHANE(shell)".
    expect(ids(await catalogue().search('('))).toEqual(['16']);
  });

  it('is case-insensitive', async () => {
    expect(ids(await catalogue().search('BackPack'))).toEqual(['1']);
  });

  it('requires EVERY term to match (3 products), not any term (which would be 5)', async () => {
    expect(ids(await catalogue().search('cotton jacket'))).toEqual(['3', '17', '16']);
  });

  it('returns an empty list when nothing matches', async () => {
    expect(await catalogue().search('submarine')).toEqual([]);
  });

  it('returns an empty list for a whitespace-only query', async () => {
    expect(await catalogue().search('   ')).toEqual([]);
  });
});

describe('product detail', () => {
  it('returns the product for a known id', async () => {
    expect((await catalogue().getProduct('12')).title).toContain('WD 4TB');
  });

  it('throws NotFoundError for an unknown id', async () => {
    await expect(catalogue().getProduct('999')).rejects.toThrow(NotFoundError);
  });
});
