import { describe, expect, it } from 'vitest';
import { ConfigError, loadConfig } from '../src/config';

describe('loadConfig', () => {
  it('applies documented defaults when nothing is set', () => {
    expect(loadConfig({})).toEqual({
      port: 3000,
      productSource: 'fakestore',
      fakeStoreBaseUrl: 'https://fakestoreapi.com',
      productCacheTtlMs: 60_000,
      featuredProductIds: undefined,
      email: { transport: 'file', smtp: undefined, salesInbox: 'sales@example.com', from: 'catalogue@example.com' },
    });
  });

  it('treats empty values (KEY= in .env) as not set, so "" does not become a 0ms cache', () => {
    const config = loadConfig({ PRODUCT_CACHE_TTL_MS: '', FEATURED_PRODUCT_IDS: '' });

    expect(config.productCacheTtlMs).toBe(60_000);
    expect(config.featuredProductIds).toBeUndefined();
  });

  it('reads the cache TTL from the environment', () => {
    expect(loadConfig({ PRODUCT_CACHE_TTL_MS: '5000' }).productCacheTtlMs).toBe(5000);
  });

  it('rejects a non-numeric cache TTL', () => {
    expect(() => loadConfig({ PRODUCT_CACHE_TTL_MS: 'soon' })).toThrow(ConfigError);
  });
});

describe('PRODUCT_SOURCE', () => {
  it('selects the snapshot source', () => {
    expect(loadConfig({ PRODUCT_SOURCE: 'snapshot' }).productSource).toBe('snapshot');
  });

  it('rejects an unknown source', () => {
    expect(() => loadConfig({ PRODUCT_SOURCE: 'postgres' })).toThrow(ConfigError);
  });
});

describe('FEATURED_PRODUCT_IDS startup format check', () => {
  it('parses comma-separated ids, trimming spaces, keeping order', () => {
    expect(loadConfig({ FEATURED_PRODUCT_IDS: '1, 5 ,9' }).featuredProductIds).toEqual(['1', '5', '9']);
  });

  it('accepts ids that do not exist yet: existence is checked per request, with a warning', () => {
    expect(loadConfig({ FEATURED_PRODUCT_IDS: '1,2,999' }).featuredProductIds).toEqual(['1', '2', '999']);
  });

  it.each([
    ['more than 5 ids', '1,2,3,4,5,6', /at most 5/],
    ['duplicate ids', '1,2,1', /duplicate/],
    ['an empty entry', '1,,2', /comma-separated/],
    ['junk characters', '1;2', /comma-separated/],
    ['an id with a space inside', '1 2,3', /comma-separated/],
  ])('refuses to start with %s', (_label, value, message) => {
    expect(() => loadConfig({ FEATURED_PRODUCT_IDS: value })).toThrow(message);
  });
});

describe('email transport', () => {
  it('rejects an unknown transport', () => {
    expect(() => loadConfig({ EMAIL_TRANSPORT: 'pigeon' })).toThrow(ConfigError);
  });

  it('requires SMTP_HOST when EMAIL_TRANSPORT=smtp', () => {
    expect(() => loadConfig({ EMAIL_TRANSPORT: 'smtp' })).toThrow(/SMTP_HOST is required/);
  });

  it('builds the smtp settings when EMAIL_TRANSPORT=smtp and a host is given', () => {
    const config = loadConfig({ EMAIL_TRANSPORT: 'smtp', SMTP_HOST: 'mailpit', SMTP_PORT: '1025' });

    expect(config.email.smtp).toEqual({ host: 'mailpit', port: 1025 });
  });

  it('ignores SMTP settings when the transport is file', () => {
    expect(loadConfig({ SMTP_HOST: 'mailpit' }).email.smtp).toBeUndefined();
  });

  it('rejects an invalid sales inbox address', () => {
    expect(() => loadConfig({ SALES_INBOX_EMAIL: 'not-an-email' })).toThrow(ConfigError);
  });
});
