import type { Category, Product } from '../domain/types';

/**
 * Where product data comes from. The domain depends only on this interface.
 *
 * Implementations today: FakeStoreProductSource, and CachingProductSource, which
 * wraps any other ProductSource. A future PostgresProductSource would implement
 * this same interface and be swapped in at the composition root (app.ts).
 *
 * Contract every implementation must honour:
 * - Return our domain types only. No source-specific shapes.
 * - "Not found" is a normal result (`null`), not an error.
 * - If the source itself fails (unreachable, timeout, unparseable), reject with
 *   UpstreamError from domain/errors.
 */
export interface ProductSource {
  /** Every product in the catalogue. Ordering is not guaranteed; the domain sorts. */
  listProducts(): Promise<Product[]>;

  /** One product by our id, or `null` if it does not exist. */
  getProduct(id: string): Promise<Product | null>;

  /** Every category, in a stable order. */
  listCategories(): Promise<Category[]>;
}
