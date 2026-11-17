import { z } from 'zod';

/** Thrown at startup when the environment is invalid. The server refuses to start. */
export class ConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ConfigError';
  }
}

export interface AppConfig {
  port: number;
  /** Where product data comes from: the live Fake Store API, or its dated local snapshot. */
  productSource: 'fakestore' | 'snapshot';
  fakeStoreBaseUrl: string;
  productCacheTtlMs: number;
  /** Merchant-chosen home page ids, or undefined to use the top-rated rule. */
  featuredProductIds: string[] | undefined;
  email: {
    transport: 'file' | 'smtp';
    /** Present only when transport is 'smtp'. */
    smtp: { host: string; port: number } | undefined;
    salesInbox: string;
    from: string;
  };
}

const MAX_FEATURED_IDS = 5;

// Ids are opaque strings to us, so the check is "looks like an id" (letters, digits,
// "-" or "_"), not "is a number". Requiring numbers would bake Fake Store's id format
// into our config. Whether an id EXISTS is checked per request, with a warning.
const ID_PATTERN = /^[A-Za-z0-9_-]+$/;

const featuredIdsSchema = z
  .string()
  .transform((raw) => raw.split(',').map((id) => id.trim()))
  .refine((ids) => ids.every((id) => ID_PATTERN.test(id)), {
    message: 'must be comma-separated ids using only letters, digits, "-" or "_" (e.g. 1,5,9)',
  })
  .refine((ids) => ids.length <= MAX_FEATURED_IDS, {
    message: `must list at most ${MAX_FEATURED_IDS} ids`,
  })
  .refine((ids) => new Set(ids).size === ids.length, {
    message: 'must not contain duplicate ids',
  })
  .optional();

const envSchema = z
  .object({
    // z.coerce turns the env string "3000" into the number 3000 before validating it.
    PORT: z.coerce.number().int().min(1).max(65535).default(3000),
    PRODUCT_SOURCE: z.enum(['fakestore', 'snapshot']).default('fakestore'),
    FAKESTORE_BASE_URL: z.url().default('https://fakestoreapi.com'),
    PRODUCT_CACHE_TTL_MS: z.coerce.number().int().nonnegative().default(60_000),
    FEATURED_PRODUCT_IDS: featuredIdsSchema,
    EMAIL_TRANSPORT: z.enum(['file', 'smtp']).default('file'),
    SMTP_HOST: z.string().optional(),
    SMTP_PORT: z.coerce.number().int().min(1).max(65535).default(1025),
    SALES_INBOX_EMAIL: z.email().default('sales@example.com'),
    EMAIL_FROM: z.email().default('catalogue@example.com'),
  })
  // A rule across two fields: SMTP_HOST is only required when the smtp transport is chosen.
  .refine((env) => env.EMAIL_TRANSPORT !== 'smtp' || env.SMTP_HOST !== undefined, {
    message: 'SMTP_HOST is required when EMAIL_TRANSPORT=smtp',
    path: ['SMTP_HOST'],
  });

/**
 * Reads and validates configuration from environment variables. Throws ConfigError
 * listing every problem, so a bad .env fails at startup rather than mid-request.
 *
 * Takes `env` as a parameter (defaulting to process.env) so tests can pass a plain object.
 */
export function loadConfig(env: NodeJS.ProcessEnv = process.env): AppConfig {
  const result = envSchema.safeParse(withoutEmptyValues(env));
  if (!result.success) {
    throw new ConfigError(`Invalid configuration:\n${z.prettifyError(result.error)}`);
  }

  const e = result.data;
  return {
    port: e.PORT,
    productSource: e.PRODUCT_SOURCE,
    fakeStoreBaseUrl: e.FAKESTORE_BASE_URL,
    productCacheTtlMs: e.PRODUCT_CACHE_TTL_MS,
    featuredProductIds: e.FEATURED_PRODUCT_IDS,
    email: {
      transport: e.EMAIL_TRANSPORT,
      smtp: e.EMAIL_TRANSPORT === 'smtp' && e.SMTP_HOST ? { host: e.SMTP_HOST, port: e.SMTP_PORT } : undefined,
      salesInbox: e.SALES_INBOX_EMAIL,
      from: e.EMAIL_FROM,
    },
  };
}

// `KEY=` in .env arrives as an empty string. Treat it as "not set" so defaults apply.
// Without this, z.coerce.number() would turn "" into 0 and silently disable the cache.
function withoutEmptyValues(env: NodeJS.ProcessEnv): Record<string, string> {
  const cleaned: Record<string, string> = {};
  for (const [key, value] of Object.entries(env)) {
    if (value !== undefined && value.trim() !== '') {
      cleaned[key] = value;
    }
  }
  return cleaned;
}
