import type { AppConfig } from '../config';
import type { ProductSource } from '../ports/ProductSource';
import { FakeStoreProductSource } from './fakestore/FakeStoreProductSource';
import { SnapshotProductSource } from './fakestore/SnapshotProductSource';

/**
 * Picks the product data source from ONE config value, PRODUCT_SOURCE. Called only from
 * the composition root (app.ts). A future Postgres source is one more case here.
 */
export function createProductSource(config: AppConfig, snapshotDirectory: string): ProductSource {
  switch (config.productSource) {
    case 'fakestore':
      return new FakeStoreProductSource({ baseUrl: config.fakeStoreBaseUrl });
    case 'snapshot':
      return new SnapshotProductSource(snapshotDirectory);
  }
}
