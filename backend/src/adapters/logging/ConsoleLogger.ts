import type { Logger } from '../../ports/Logger';

/** Logger that writes to the console. Enough for a PoC; swap for a structured logger later. */
export class ConsoleLogger implements Logger {
  info(message: string, context?: Record<string, unknown>): void {
    console.info(`[info] ${message}`, formatContext(context));
  }

  warn(message: string, context?: Record<string, unknown>): void {
    console.warn(`[warn] ${message}`, formatContext(context));
  }

  error(message: string, context?: Record<string, unknown>): void {
    console.error(`[error] ${message}`, formatContext(context));
  }
}

// Context goes out as a JSON string so one event stays on one log line.
function formatContext(context?: Record<string, unknown>): string {
  return context ? JSON.stringify(context) : '';
}
