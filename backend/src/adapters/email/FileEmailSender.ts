import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { EmailError } from '../../domain/errors';
import type { EmailMessage, EmailSender } from '../../ports/EmailSender';
import type { Logger } from '../../ports/Logger';

/**
 * EmailSender that writes each message to a .eml file instead of sending it.
 * The default transport: no mail server, no Docker, works everywhere. Open the file
 * in any mail client, or read it as text.
 */
export class FileEmailSender implements EmailSender {
  constructor(
    private readonly directory: string,
    private readonly logger: Logger,
    // Injected so tests can pin the timestamp and id; production uses the real ones.
    private readonly now: () => Date = () => new Date(),
    private readonly newId: () => string = randomUUID,
  ) {}

  async send(message: EmailMessage): Promise<void> {
    // The filename is GENERATED from a timestamp plus a random UUID. It never comes
    // from user input (product id, customer name, ...), so input such as "../../x"
    // can never choose where the file is written: no path traversal.
    const timestamp = this.now().toISOString().replace(/[:.]/g, '-');
    const path = join(this.directory, `${timestamp}-${this.newId()}.eml`);

    try {
      // recursive: true creates backend/data/enquiries on first use, and is a no-op after.
      await mkdir(this.directory, { recursive: true });
      // flag 'wx' = create only; fail rather than overwrite an existing file.
      await writeFile(path, toEml(message, this.now()), { encoding: 'utf8', flag: 'wx' });
    } catch (error) {
      throw new EmailError('Could not write enquiry email file', { cause: error });
    }

    this.logger.info(`Enquiry email written to ${path}`, { to: message.to });
  }
}

/** Builds a minimal RFC 5322 message: headers, blank line, plain-text body, CRLF line endings. */
function toEml(message: EmailMessage, date: Date): string {
  const headers = [
    ['From', message.from],
    ['To', message.to],
    ...(message.replyTo ? [['Reply-To', message.replyTo]] : []),
    ['Subject', message.subject],
    ['Date', date.toUTCString()],
    ['MIME-Version', '1.0'],
    ['Content-Type', 'text/plain; charset=utf-8'],
  ];

  // We write headers by hand here, so this adapter strips CR/LF itself rather than
  // trusting every caller to have done it. One header per line, always.
  const headerLines = headers.map(([name, value]) => `${name}: ${stripLineBreaks(value ?? '')}`);
  const body = message.text.replace(/\r?\n/g, '\r\n');
  return `${headerLines.join('\r\n')}\r\n\r\n${body}`;
}

function stripLineBreaks(value: string): string {
  return value.replace(/[\r\n]+/g, ' ');
}
