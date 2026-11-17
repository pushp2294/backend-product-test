/**
 * Every log line in the backend goes through this port: the rule is that nothing
 * bypasses it with console.* directly.
 *
 * info / warn / error, and that is final: no debug or trace. It is a port, not a
 * logging framework. It exists so tests can inject a recording logger and assert
 * what was logged, and so the destination (console today) can change in one place.
 */
export interface Logger {
  /** Normal events worth a line, e.g. "enquiry email written to <file>". */
  info(message: string, context?: Record<string, unknown>): void;
  /** Bad but survivable, e.g. an unknown id in FEATURED_PRODUCT_IDS, or the upstream is down. */
  warn(message: string, context?: Record<string, unknown>): void;
  /** A bug on our side: an unexpected error that became a 500. */
  error(message: string, context?: Record<string, unknown>): void;
}
