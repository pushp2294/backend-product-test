// Composition root: the ONLY place concrete adapters are chosen and wired to ports.
// Swapping the data source or email provider is a change here and nowhere else.
import { join } from 'node:path';
import express, { type Express } from 'express';
import { CachingProductSource } from './adapters/caching/CachingProductSource';
import { createEmailSender } from './adapters/email/createEmailSender';
import { createProductSource } from './adapters/createProductSource';
import { ConsoleLogger } from './adapters/logging/ConsoleLogger';
import type { AppConfig } from './config';
import { CatalogueService } from './domain/catalogue';
import { EnquiryService } from './domain/enquiries';
import { createErrorHandler, notFoundHandler } from './http/errorHandler';
import { createRouter } from './http/routes';
import type { Logger } from './ports/Logger';

export const API_PREFIX = '/api/v1';

// backend/data/enquiries, resolved from this file's folder (src/ under tsx, dist/ when
// built), so it is the same place whatever directory the server is started from.
export const ENQUIRY_DIRECTORY = join(__dirname, '..', 'data', 'enquiries');

// backend/snapshot: the dated Fake Store snapshot served when PRODUCT_SOURCE=snapshot.
export const SNAPSHOT_DIRECTORY = join(__dirname, '..', 'snapshot');

/** Everything the HTTP app needs. Tests build this with fakes; production uses composeApp. */
export interface AppDependencies {
  catalogue: CatalogueService;
  enquiries: EnquiryService;
  logger: Logger;
}

/** Builds the Express app from already-constructed dependencies. No adapter choices here. */
export function buildApp({ catalogue, enquiries, logger }: AppDependencies): Express {
  const app = express();

  // Don't advertise the framework in an X-Powered-By header (security basics).
  app.disable('x-powered-by');

  // Parse JSON bodies, capped at 10kb: an enquiry is tiny, so anything bigger is abuse.
  app.use(express.json({ limit: '10kb' }));

  app.use(API_PREFIX, createRouter(catalogue, enquiries));

  // Order matters: unmatched requests fall through to 404, then errors to the handler.
  app.use(notFoundHandler);
  app.use(createErrorHandler(logger));

  return app;
}

/** Production wiring: picks the real adapters based on config. */
export function composeApp(config: AppConfig, logger: Logger = new ConsoleLogger()): Express {
  // One config value, PRODUCT_SOURCE, picks the data source; the cache decorator wraps
  // whichever it is. Nothing below this line knows or cares which source was chosen.
  const productSource = new CachingProductSource(
    createProductSource(config, SNAPSHOT_DIRECTORY),
    config.productCacheTtlMs,
    logger,
  );

  const catalogue = new CatalogueService(productSource, logger, {
    featuredProductIds: config.featuredProductIds,
  });

  // One config value, EMAIL_TRANSPORT, picks the email adapter.
  const enquiries = new EnquiryService(
    productSource,
    createEmailSender(config.email, ENQUIRY_DIRECTORY, logger),
    { salesInbox: config.email.salesInbox, from: config.email.from },
  );

  return buildApp({ catalogue, enquiries, logger });
}
