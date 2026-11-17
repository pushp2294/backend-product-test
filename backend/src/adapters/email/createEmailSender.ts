import type { AppConfig } from '../../config';
import type { EmailSender } from '../../ports/EmailSender';
import type { Logger } from '../../ports/Logger';
import { FileEmailSender } from './FileEmailSender';
import { SmtpEmailSender } from './SmtpEmailSender';

/**
 * Picks the EmailSender from ONE config value, EMAIL_TRANSPORT. Called only from the
 * composition root (app.ts). Kept in its own function so the choice is unit-testable.
 */
export function createEmailSender(email: AppConfig['email'], enquiryDirectory: string, logger: Logger): EmailSender {
  switch (email.transport) {
    case 'smtp':
      if (!email.smtp) {
        // Unreachable when config came from loadConfig(), which requires SMTP_HOST for smtp.
        throw new Error('EMAIL_TRANSPORT=smtp but no SMTP settings were provided');
      }
      return SmtpEmailSender.fromConfig(email.smtp);
    case 'file':
      return new FileEmailSender(enquiryDirectory, logger);
  }
}
