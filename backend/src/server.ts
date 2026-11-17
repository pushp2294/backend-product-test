// Entrypoint: load config, build the app, start listening.
import { composeApp } from './app';
import { ConfigError, loadConfig } from './config';
import { ConsoleLogger } from './adapters/logging/ConsoleLogger';

const logger = new ConsoleLogger();

try {
  const config = loadConfig();
  const app = composeApp(config, logger);

  // Express 5 calls this callback on failure too (e.g. port already in use), passing the
  // error. Without this check we would log "listening" for a server that never started.
  app.listen(config.port, (error?: Error) => {
    if (error) {
      logger.error(`Could not start the backend on port ${config.port}: ${error.message}`, {
        hint: 'Another process is using this port. Stop it, or set PORT in .env.',
      });
      process.exit(1);
    }
    logger.info(`Backend listening on http://localhost:${config.port}/api/v1`, {
      productSource: config.productSource,
      emailTransport: config.email.transport,
      productCacheTtlMs: config.productCacheTtlMs,
      featuredProductIds: config.featuredProductIds ?? 'top-rated rule',
    });
  });
} catch (error) {
  // A bad .env must stop the server with a readable message, not a stack trace.
  if (error instanceof ConfigError) {
    logger.warn(error.message);
    process.exit(1);
  }
  throw error;
}
