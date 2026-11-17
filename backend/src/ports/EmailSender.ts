/**
 * A plain-text email, provider-neutral.
 *
 * Plain text only: customer-entered values go straight into the body, and plain text
 * cannot carry HTML or script injection.
 */
export interface EmailMessage {
  to: string;
  /** Our sending address (from config). Never the customer's address. */
  from: string;
  /** The customer's address, so "Reply" in the sales inbox goes to them. */
  replyTo?: string;
  subject: string;
  text: string;
}

/**
 * How emails leave the system. The domain depends only on this interface.
 *
 * Implementations today: FileEmailSender (default, writes .eml files) and
 * SmtpEmailSender (nodemailer). Chosen at the composition root by EMAIL_TRANSPORT.
 *
 * Contract: resolve once the provider has accepted the message; otherwise reject
 * with EmailError from domain/errors.
 */
export interface EmailSender {
  send(message: EmailMessage): Promise<void>;
}
