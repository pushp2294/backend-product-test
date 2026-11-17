// Zod schemas for Fake Store's OWN response shapes.
//
// Boundary rule: this file is imported only by FakeStoreProductSource.ts in this folder.
// test/architecture.test.ts fails the build if anything else imports it, so Fake Store
// shapes cannot leak into the domain or HTTP layer.
import { z } from 'zod';

// Validating upstream data at runtime (not just typing it) means a change on their
// side becomes a clear UpstreamError here instead of `undefined` deep in our code.
export const fakeStoreProductSchema = z.object({
  id: z.number().int(),
  title: z.string(),
  price: z.number().nonnegative(),
  description: z.string(),
  category: z.string(),
  image: z.url(),
  rating: z.object({
    rate: z.number(),
    count: z.number().int(),
  }),
});

export const fakeStoreProductListSchema = z.array(fakeStoreProductSchema);

export const fakeStoreCategoryListSchema = z.array(z.string());

// z.infer derives the TypeScript type from the schema, so the two can never drift apart.
export type FakeStoreProduct = z.infer<typeof fakeStoreProductSchema>;
