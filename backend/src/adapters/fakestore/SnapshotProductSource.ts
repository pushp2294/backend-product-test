import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { UpstreamError } from '../../domain/errors';
import type { Category, Product } from '../../domain/types';
import type { ProductSource } from '../../ports/ProductSource';
import { decodeCategories, decodeProducts } from './fakeStoreMapping';

/**
 * ProductSource that serves a dated local snapshot of the Fake Store API (the files in
 * backend/snapshot/) instead of calling it. Selected with PRODUCT_SOURCE=snapshot.
 *
 * Uses the same validation and mapping as FakeStoreProductSource, so the domain sees
 * exactly the same products either way. Useful offline, or when Fake Store is slow or down.
 */
export class SnapshotProductSource implements ProductSource {
  constructor(private readonly directory: string) {}

  async listProducts(): Promise<Product[]> {
    return decodeProducts(await this.read('fakestore-products.json'), 'snapshot products');
  }

  async getProduct(id: string): Promise<Product | null> {
    // The snapshot is small (20 products), so a lookup in the full list is enough.
    return (await this.listProducts()).find((product) => product.id === id) ?? null;
  }

  async listCategories(): Promise<Category[]> {
    return decodeCategories(await this.read('fakestore-categories.json'), 'snapshot categories');
  }

  /** A missing or unreadable snapshot file is a source failure, like an unreachable API. */
  private async read(file: string): Promise<string> {
    const path = join(this.directory, file);
    try {
      return await readFile(path, 'utf8');
    } catch (error) {
      throw new UpstreamError(`Product snapshot could not be read: ${path}`, { cause: error });
    }
  }
}
