import type { Logger } from '../ports/Logger';
import type { ProductSource } from '../ports/ProductSource';
import { NotFoundError } from './errors';
import type { Category, Product } from './types';

/** The home page shows this many featured products (AC: "I see those 5 featured products"). */
export const FEATURED_COUNT = 5;

/**
 * Top-rated products shown first on a category page. 2, not 3: two live categories
 * have only 4 products, and N = 3 would leave a single "additional" product.
 */
export const CATEGORY_TOP_RATED_COUNT = 2;

export interface CategoryProducts {
  category: Category;
  topRated: Product[];
  others: Product[];
}

export interface CatalogueOptions {
  /**
   * Merchant-chosen product ids for the home page, in display order (from
   * FEATURED_PRODUCT_IDS). Absent = use the top-rated rule for all 5 slots.
   */
  featuredProductIds?: readonly string[];
}

/**
 * The "top rated" rule, used everywhere products are ranked: rating desc, then
 * number of ratings desc, then id asc so the order is always deterministic.
 */
export function compareTopRated(a: Product, b: Product): number {
  return (
    b.rating.average - a.rating.average ||
    b.rating.count - a.rating.count ||
    // numeric: true sorts "9" before "10"; plain string order would put "10" first.
    a.id.localeCompare(b.id, 'en', { numeric: true })
  );
}

/** Returns a NEW sorted array. Never sorts in place: the input may be a shared cached array. */
export function rankTopRated(products: readonly Product[]): Product[] {
  return [...products].sort(compareTopRated);
}

/**
 * Backslash-escapes every character that has a meaning in a regular expression, so
 * the string matches only itself. (Standard pattern from MDN's RegExp guide.)
 */
export function escapeRegExp(text: string): string {
  // `$&` in the replacement means "the character that was matched", so "(" becomes "\(".
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** Catalogue use cases. Depends only on ports, never on a concrete adapter. */
export class CatalogueService {
  /**
   * Unknown curated ids already warned about. Config cannot change while the process
   * runs, so one warning per id is enough; repeating it per request is log spam.
   */
  private readonly warnedUnknownIds = new Set<string>();

  constructor(
    private readonly source: ProductSource,
    private readonly logger: Logger,
    private readonly options: CatalogueOptions = {},
  ) {}

  /**
   * Home page products: always FEATURED_COUNT of them (if the catalogue has that many).
   *
   * Curated ids come first, in config order. Unknown curated ids are logged as a
   * warning, and every slot not filled by a curated product is filled from the
   * top-rated rule, skipping products already shown. A mistyped id must not leave
   * the home page with 4 products.
   */
  async getFeatured(): Promise<Product[]> {
    const ranked = rankTopRated(await this.source.listProducts());
    const curatedIds = this.options.featuredProductIds ?? [];

    const byId = new Map(ranked.map((product) => [product.id, product]));
    const curated: Product[] = [];
    const unknownIds: string[] = [];
    for (const id of curatedIds) {
      const product = byId.get(id);
      if (product) {
        curated.push(product);
      } else {
        unknownIds.push(id);
      }
    }

    const newlyUnknown = unknownIds.filter((id) => !this.warnedUnknownIds.has(id));
    if (newlyUnknown.length > 0) {
      newlyUnknown.forEach((id) => this.warnedUnknownIds.add(id));
      this.logger.warn(
        `FEATURED_PRODUCT_IDS has unknown product ids (${newlyUnknown.join(', ')}); filling from the top-rated rule`,
        { unknownIds: newlyUnknown },
      );
    }

    const curatedIdSet = new Set(curated.map((product) => product.id));
    const fill = ranked
      .filter((product) => !curatedIdSet.has(product.id))
      .slice(0, Math.max(0, FEATURED_COUNT - curated.length));

    return [...curated, ...fill].slice(0, FEATURED_COUNT);
  }

  listCategories(): Promise<Category[]> {
    return this.source.listCategories();
  }

  /** Top-rated products in a category, then the rest of that category, with no overlap. */
  async getCategoryProducts(categoryId: string): Promise<CategoryProducts> {
    // The two calls don't depend on each other, so they run in PARALLEL: the worst case
    // is one upstream timeout (~5s), not two back to back (~10s). Promise.all rejects
    // as soon as either fails. A test fails if these are made sequential again.
    const [categories, products] = await Promise.all([this.source.listCategories(), this.source.listProducts()]);

    const category = categories.find((c) => c.id === categoryId);
    if (!category) {
      throw new NotFoundError('category', categoryId);
    }

    const inCategory = products.filter((p) => p.category.id === categoryId);
    const ranked = rankTopRated(inCategory);

    return {
      category,
      topRated: ranked.slice(0, CATEGORY_TOP_RATED_COUNT),
      others: ranked.slice(CATEGORY_TOP_RATED_COUNT),
    };
  }

  /**
   * Products where EVERY whitespace-separated term matches the START of some word in
   * the title or description, case-insensitively, ordered by the top-rated rule.
   *
   * Word-start prefix matching: "back" finds "backpack", and "men" finds "Men's" but
   * not "Women's" (the "m" in "women" is not at the start of a word).
   */
  async search(query: string): Promise<Product[]> {
    const patterns = query
      .split(/\s+/)
      .filter((term) => term.length > 0)
      // \b = word boundary, so the term must begin a word. The term is user input, so it
      // is escaped first: "(" or ".*" must be literal text, never regex syntax.
      .map((term) => new RegExp(`\\b${escapeRegExp(term)}`, 'i'));

    // The HTTP layer already rejects a blank query; this guard keeps the domain safe on its own.
    if (patterns.length === 0) {
      return [];
    }

    const matches = (await this.source.listProducts()).filter((product) => {
      // Joined with a newline so no match can run across the join between title and description.
      const text = `${product.title}\n${product.description}`;
      return patterns.every((pattern) => pattern.test(text));
    });

    return rankTopRated(matches);
  }

  async getProduct(id: string): Promise<Product> {
    const product = await this.source.getProduct(id);
    if (!product) {
      throw new NotFoundError('product', id);
    }
    return product;
  }
}
