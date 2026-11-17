// Our own domain shapes. Every adapter maps INTO these; nothing outside an adapter
// ever sees a data source's raw response shape.

/**
 * A price in integer minor units (cents/pence) plus an ISO 4217 currency code.
 * Integers avoid floating-point rounding errors (0.1 + 0.2 !== 0.3).
 */
export interface Money {
  amountMinor: number;
  currency: string;
}

export interface Rating {
  /** Average score, 0–5. */
  average: number;
  /** Number of ratings the average is based on. */
  count: number;
}

export interface Category {
  /** Our own URL-safe slug, e.g. "mens-clothing". Never a data source's raw string. */
  id: string;
  /** Human-readable name for display. */
  name: string;
}

export interface Product {
  /** Always a string, whatever the source uses (Fake Store: numbers; Postgres: maybe UUIDs). */
  id: string;
  title: string;
  description: string;
  price: Money;
  /** Opaque URL the client loads directly. See DECISIONS.md on image URLs. */
  imageUrl: string;
  rating: Rating;
  category: Category;
}

/** What a customer submits from the enquiry form, after validation. */
export interface Enquiry {
  productId: string;
  name: string;
  email: string;
  phone?: string;
  message?: string;
}
