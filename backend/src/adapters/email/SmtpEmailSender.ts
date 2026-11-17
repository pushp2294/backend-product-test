import nodemailer, { type SendMailOptions } from 'nodemailer';
import { EmailError } from '../../domain/errors';
import type { EmailMessage, EmailSender } from '../../ports/EmailSender';

/** The one nodemailer method we use. Narrow type so tests can pass a stub transport. */
export interface MailTransport {
  sendMail(mail: SendMailOptions): Promise<unknown>;
}

/**
 * EmailSender over SMTP via nodemailer. Selected with EMAIL_TRANSPORT=smtp.
 * Works with any SMTP server (e.g. a local Mailpit); in production any relay (AWS SES speaks SMTP),
 * so moving providers is a config change.
 */
export class SmtpEmailSender implements EmailSender {
  constructor(private readonly transport: MailTransport) {}

  /** Production factory: a real SMTP connection to host:port. */
  static fromConfig(smtp: { host: string; port: number }): SmtpEmailSender {
    return new SmtpEmailSender(
      nodemailer.createTransport({
        host: smtp.host,
        port: smtp.port,
        // Mailpit and most local relays speak plain SMTP; TLS would be configured for production.
        secure: false,
        // Nodemailer's defaults wait up to 2 minutes. These bound the enquiry request so the
        // app's 15s client timeout is never reached first: an unreachable server fails at
        // connect (5s); one that accepts but stalls fails at greeting or socket (5s each).
        connectionTimeout: 5_000,
        greetingTimeout: 5_000,
        socketTimeout: 5_000,
      }),
    );
  }

  async send(message: EmailMessage): Promise<void> {
    try {
      await this.transport.sendMail({
        from: message.from,
        to: message.to,
        replyTo: message.replyTo,
        subject: message.subject,
        // `text` only, no `html`: the email is plain text by design.
        text: message.text,
      });
    } catch (error) {
      throw new EmailError('SMTP server did not accept the enquiry email', { cause: error });
    }
  }
}
