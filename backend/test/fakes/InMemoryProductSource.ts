import { FakeStoreProductSource } from '../../src/adapters/fakestore/FakeStoreProductSource';
import type { Category, Product } from '../../src/domain/types';
import type { ProductSource } from '../../src/ports/ProductSource';
import { fixture, stubFetch } from './stubFetch';

/** A ProductSource over a fixed list of domain products. The fake for domain and route tests. */
export class InMemoryProductSource implements ProductSource {
  constructor(
    private readonly products: Product[],
    private readonly categories: Category[],
  ) {}

  async listProducts(): Promise<Product[]> {
    return this.products;
  }

  async getProduct(id: string): Promise<Product | null> {
    return this.products.find((p) => p.id === id) ?? null;
  }

  async listCategories(): Promise<Category[]> {
    return this.categories;
  }
}

/**
 * The live 2026-11-16 snapshot as domain products, mapped by the real Fake Store
 * adapter over a stubbed network. Using the real mapping (not a hand copy) means
 * domain tests run on exactly the data the app would see.
 */
export async function liveSnapshotSource(): Promise<InMemoryProductSource> {
  const { fetchFn } = stubFetch({
    '/products': { status: 200, body: fixture('fakestore-products.json') },
    '/products/categories': { status: 200, body: fixture('fakestore-categories.json') },
  });
  const adapter = new FakeStoreProductSource({ baseUrl: 'https://fakestore.test', fetchFn });
  return new InMemoryProductSource(await adapter.listProducts(), await adapter.listCategories());
}
