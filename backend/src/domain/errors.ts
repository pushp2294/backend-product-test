// Domain errors. The HTTP layer maps each class to a status code and error code in
// one place (http/errorHandler.ts), so the domain never knows about HTTP.
//
// Adapters may import these: dependencies point inward (adapter -> domain), never
// the other way round.

/** A product or category that the caller asked for does not exist. */
export class NotFoundError extends Error {
  constructor(
    readonly resource: 'product' | 'category',
    readonly id: string,
  ) {
    super(`${resource} not found: ${id}`);
    // Without this, error.name would be the generic "Error" in logs and stack traces.
    this.name = 'NotFoundError';
  }
}

/** The product data source failed: unreachable, timed out, or returned something we cannot parse. */
export class UpstreamError extends Error {
  // `cause` keeps the original error for server-side logs without exposing it to clients.
  constructor(message: string, options?: { cause?: unknown }) {
    super(message, options);
    this.name = 'UpstreamError';
  }
}

/** The email provider did not accept the message. */
export class EmailError extends Error {
  constructor(message: string, options?: { cause?: unknown }) {
    super(message, options);
    this.name = 'EmailError';
  }
}
