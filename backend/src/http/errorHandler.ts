// The ONE place errors become HTTP responses. Every error leaves the API in the same
// shape: { "error": { "code", "message", "details"? } }. See docs/PLAN.md section 3.
import type { ErrorRequestHandler, RequestHandler } from 'express';
import { ZodError } from 'zod';
import { EmailError, NotFoundError, UpstreamError } from '../domain/errors';
import type { Logger } from '../ports/Logger';

export type ErrorCode =
  | 'VALIDATION_ERROR'
  | 'NOT_FOUND'
  | 'PAYLOAD_TOO_LARGE'
  | 'UPSTREAM_UNAVAILABLE'
  | 'EMAIL_FAILED'
  | 'INTERNAL_ERROR';

export interface ErrorBody {
  error: {
    code: ErrorCode;
    message: string;
    details?: Array<{ field: string; message: string }>;
  };
}

function errorBody(code: ErrorCode, message: string, details?: ErrorBody['error']['details']): ErrorBody {
  return { error: details ? { code, message, details } : { code, message } };
}

/** Catch-all for paths and methods no route matched. Registered after all routes. */
export const notFoundHandler: RequestHandler = (_req, res) => {
  res.status(404).json(errorBody('NOT_FOUND', 'Resource not found'));
};

/**
 * Maps known errors to status + code. Messages are written for API clients and never
 * include internals: no upstream name, no stack trace, no `cause`. Those go to the log.
 */
export function createErrorHandler(logger: Logger): ErrorRequestHandler {
  // Express recognises error middleware by its FOUR parameters, so `_next` must stay
  // in the signature even though it is unused.
  return (err: unknown, _req, res, _next) => {
    if (err instanceof ZodError) {
      // An issue with an empty path is about the whole body (e.g. no JSON body at all).
      const details = err.issues.map((issue) => ({ field: issue.path.join('.') || 'body', message: issue.message }));
      res.status(400).json(errorBody('VALIDATION_ERROR', 'Request is invalid', details));
      return;
    }

    if (err instanceof NotFoundError) {
      res.status(404).json(errorBody('NOT_FOUND', `The ${err.resource} was not found`));
      return;
    }

    if (err instanceof UpstreamError) {
      logger.warn('Product source failure', { error: err.message, cause: describe(err.cause) });
      res.status(502).json(errorBody('UPSTREAM_UNAVAILABLE', 'Product data is temporarily unavailable'));
      return;
    }

    if (err instanceof EmailError) {
      logger.warn('Email delivery failure', { error: err.message, cause: describe(err.cause) });
      res.status(502).json(errorBody('EMAIL_FAILED', 'Your enquiry could not be sent. Please try again.'));
      return;
    }

    // express.json() tags body-parsing failures with a `type` field.
    const bodyErrorType = (err as { type?: unknown } | null)?.type;
    if (bodyErrorType === 'entity.parse.failed') {
      res.status(400).json(errorBody('VALIDATION_ERROR', 'Request body is not valid JSON'));
      return;
    }
    if (bodyErrorType === 'entity.too.large') {
      res.status(413).json(errorBody('PAYLOAD_TOO_LARGE', 'Request body is too large'));
      return;
    }

    // Anything else is a bug on our side: log it as an error, tell the client nothing internal.
    logger.error('Unhandled error', { error: describe(err), stack: err instanceof Error ? err.stack : undefined });
    res.status(500).json(errorBody('INTERNAL_ERROR', 'Something went wrong'));
  };
}

function describe(value: unknown): string | undefined {
  if (value === undefined) return undefined;
  return value instanceof Error ? `${value.name}: ${value.message}` : String(value);
}
