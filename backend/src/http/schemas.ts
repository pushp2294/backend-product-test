// Request validation schemas and domain -> response mappers.
// Response types are OUR API contract (docs/PLAN.md section 3). They are built from
// domain types only, so the client cannot tell which data source is behind them.
import { z } from 'zod';
import type { CategoryProducts } from '../domain/catalogue';
import type { Category, Money, Product, Rating } from '../domain/types';

// ---- Requests ----

/** GET /search?q=... : 1–100 characters after trimming. */
export const searchQuerySchema = z.object({
  // A repeated ?q=a&q=b arrives as an array; z.string() rejects it with a 400.
  q: z.string().trim().min(1, 'Enter a search term').max(100, 'Search term must be at most 100 characters'),
});

/** Path ids are opaque strings; the length cap stops absurd inputs reaching the domain. */
const pathId = z.string().min(1).max(100);

export const productParamsSchema = z.object({ id: pathId });

// Single-line fields end up in email headers (Subject, Reply-To). CR or LF there is
// header injection ("Ann\r\nBcc: x@evil.com"), so they are REJECTED here with a 400.
const SINGLE_LINE = /^[^\r\n]*$/;

/**
 * POST /enquiries body. Unknown keys are dropped (z.object's default), so any
 * product title or price the client sends is ignored: the server looks those up itself.
 */
export const enquiryBodySchema = z.object({
  productId: pathId,
  name: z.string().trim().min(1, 'Enter your name').max(100).regex(SINGLE_LINE, 'Must be a single line'),
  // z.email()'s format allows no whitespace at all, so it already rejects CR/LF. This
  // value becomes Reply-To; the domain's toHeaderValue is the second line of defence.
  email: z.email('Enter a valid email address').max(254),
  phone: z
    .string()
    .trim()
    .max(30)
    .regex(/^[0-9+\- ]*$/, 'Use digits, spaces, + or - only')
    // An empty phone box means "not given".
    .transform((value) => (value === '' ? undefined : value))
    .optional(),
  // The message goes in the email BODY only, so line breaks are allowed here.
  message: z.string().trim().max(1000).optional(),
});
export const categoryParamsSchema = z.object({ categoryId: pathId });

// ---- Responses ----

export interface ProductSummaryResponse {
  id: string;
  title: string;
  price: Money;
  imageUrl: string;
  rating: Rating;
  categoryId: string;
}

export interface ProductDetailResponse extends ProductSummaryResponse {
  description: string;
  category: Category;
}

export function toProductSummary(product: Product): ProductSummaryResponse {
  return {
    id: product.id,
    title: product.title,
    price: product.price,
    imageUrl: product.imageUrl,
    rating: product.rating,
    categoryId: product.category.id,
  };
}

export function toProductDetail(product: Product): ProductDetailResponse {
  return {
    ...toProductSummary(product),
    description: product.description,
    category: product.category,
  };
}

export function toCategoryProductsResponse(result: CategoryProducts) {
  return {
    category: result.category,
    topRated: result.topRated.map(toProductSummary),
    others: result.others.map(toProductSummary),
  };
}
