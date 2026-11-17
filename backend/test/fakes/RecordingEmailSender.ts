import type { EmailMessage, EmailSender } from '../../src/ports/EmailSender';

/** EmailSender that records messages instead of sending. Can be told to fail. */
export class RecordingEmailSender implements EmailSender {
  readonly sent: EmailMessage[] = [];

  constructor(private readonly failWith?: Error) {}

  async send(message: EmailMessage): Promise<void> {
    if (this.failWith) {
      throw this.failWith;
    }
    this.sent.push(message);
  }
}
