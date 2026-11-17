// The ONE mapping from Fake Store-shaped JSON to our domain types. Shared by the two
// adapters that read Fake Store data: FakeStoreProductSource (live API) and
// SnapshotProductSource (dated local copy). Both get identical validation and mapping.
import type { ZodType } from 'zod';
import { UpstreamError } from '../../domain/errors';
import type { Category, Product } from '../../domain/types';
import {
  type FakeStoreProduct,
  fakeStoreCategoryListSchema,
  fakeStoreProductListSchema,
  fakeStoreProductSchema,
} from './fakeStoreSchemas';

/** Fake Store does not state a currency; prices read as US dollars. Recorded in DECISIONS.md. */
const CURRENCY = 'USD';

/** JSON text -> validated products. Any problem is UpstreamError naming `source`. */
export function decodeProducts(body: string, source: string): Product[] {
  return decode(body, fakeStoreProductListSchema, source).map(toProduct);
}

export function decodeProduct(body: string, source: string): Product {
  return toProduct(decode(body, fakeStoreProductSchema, source));
}

export function decodeCategories(body: string, source: string): Category[] {
  return decode(body, fakeStoreCategoryListSchema, source).map(toCategory);
}

/** JSON-parse the body and validate it against a schema; any mismatch is UpstreamError. */
function decode<T>(body: string, schema: ZodType<T>, source: string): T {
  let json: unknown;
  try {
    json = JSON.parse(body);
  } catch (error) {
    throw new UpstreamError(`Product source returned invalid JSON: ${source}`, { cause: error });
  }

  const result = schema.safeParse(json);
  if (!result.success) {
    throw new UpstreamError(`Product source returned an unexpected shape: ${source}`, {
      cause: result.error,
    });
  }
  return result.data;
}

// ---- Mapping boundary: Fake Store shapes in, our domain types out. ----

function toProduct(raw: FakeStoreProduct): Product {
  return {
    // Our ids are strings whatever the source uses, so a UUID-keyed source fits later.
    id: String(raw.id),
    title: raw.title,
    description: raw.description,
    price: {
      // Snapshot check: no Fake Store price has more than 2 decimals, so rounding is
      // exact. Math.round is still needed: in JavaScript 1.15 * 100 = 114.99999999999999.
      amountMinor: Math.round(raw.price * 100),
      currency: CURRENCY,
    },
    imageUrl: raw.image,
    rating: { average: raw.rating.rate, count: raw.rating.count },
    category: toCategory(raw.category),
  };
}

/** "men's clothing" -> { id: "mens-clothing", name: "Men's clothing" }. */
function toCategory(raw: string): Category {
  return { id: slugify(raw), name: capitalise(raw) };
}

function slugify(value: string): string {
  return value
    .toLowerCase()
    .replace(/['’]/g, '') // drop apostrophes so "men's" becomes "mens", not "men-s"
    .replace(/[^a-z0-9]+/g, '-') // any other run of non-alphanumerics becomes one hyphen
    .replace(/^-+|-+$/g, ''); // no leading or trailing hyphens
}

// Display names are the source's own strings, capitalised; "jewelery" keeps its
// misspelling on purpose. See DECISIONS.md.
function capitalise(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}
