// Both EmailSender implementations, tested for real: FileEmailSender writes actual
// files to a temp directory; SmtpEmailSender talks SMTP over TCP to a fake server.
import { mkdtemp, readdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createEmailSender } from '../src/adapters/email/createEmailSender';
import { FileEmailSender } from '../src/adapters/email/FileEmailSender';
import { SmtpEmailSender } from '../src/adapters/email/SmtpEmailSender';
import { EmailError } from '../src/domain/errors';
import type { EmailMessage } from '../src/ports/EmailSender';
import { type FakeSmtpServer, headerLines, startFakeSmtpServer } from './fakes/fakeSmtpServer';
import { RecordingLogger } from './fakes/RecordingLogger';

const message: EmailMessage = {
  to: 'sales@example.com',
  from: 'catalogue@example.com',
  replyTo: 'ada@example.com',
  subject: 'Enquiry: WD 4TB Gaming Drive (from Ada)',
  text: 'Line one\nLine two',
};

/** A subject that tries to smuggle in a Bcc header via CRLF. */
const INJECTED_SUBJECT = 'Hello\r\nBcc: someone@evil.com';

const FILENAME = /^\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}-\d{3}Z-[0-9a-f-]{36}\.eml$/;

describe('FileEmailSender', () => {
  let dir: string;
  let logger: RecordingLogger;

  beforeEach(async () => {
    dir = await mkdtemp(join(tmpdir(), 'enquiries-'));
    logger = new RecordingLogger();
  });

  afterEach(async () => {
    await rm(dir, { recursive: true, force: true });
  });

  it('writes one plain-text .eml with From, To, Reply-To, Subject and the body, and logs the path via info()', async () => {
    await new FileEmailSender(dir, logger).send(message);

    const files = await readdir(dir);
    expect(files).toHaveLength(1);
    const eml = await readFile(join(dir, files[0]!), 'utf8');

    expect(headerLines(eml)).toEqual(
      expect.arrayContaining([
        'From: catalogue@example.com',
        'To: sales@example.com',
        'Reply-To: ada@example.com',
        'Subject: Enquiry: WD 4TB Gaming Drive (from Ada)',
        'Content-Type: text/plain; charset=utf-8',
      ]),
    );
    expect(eml).toContain('\r\n\r\nLine one\r\nLine two');
    expect(logger.infos).toHaveLength(1);
    expect(logger.infos[0]?.message).toContain(join(dir, files[0]!));
  });

  it('header injection: CR/LF in a header value cannot create an extra header', async () => {
    await new FileEmailSender(dir, logger).send({ ...message, subject: INJECTED_SUBJECT });

    const [file] = await readdir(dir);
    const headers = headerLines(await readFile(join(dir, file!), 'utf8'));

    expect(headers.some((line) => /^bcc:/i.test(line))).toBe(false);
    expect(headers).toContain('Subject: Hello Bcc: someone@evil.com');
  });

  it('path traversal: the filename is generated (timestamp + UUID), never taken from message content', async () => {
    await new FileEmailSender(dir, logger).send({
      ...message,
      subject: '../../../etc/passwd',
      replyTo: '../../evil@example.com',
    });

    const files = await readdir(dir);
    expect(files).toHaveLength(1);
    expect(files[0]).toMatch(FILENAME);
    expect(files[0]).not.toContain('..');
  });

  it('two emails in the same millisecond still get two different files', async () => {
    const frozen = () => new Date('2026-11-17T10:00:00.000Z');
    const sender = new FileEmailSender(dir, logger, frozen);

    await sender.send(message);
    await sender.send(message);

    expect(await readdir(dir)).toHaveLength(2);
  });

  it('turns a write failure into EmailError', async () => {
    // A FILE where the directory should be: mkdir fails, as it would on a read-only disk.
    const notADirectory = join(dir, 'blocker');
    await writeFile(notADirectory, 'x');

    await expect(new FileEmailSender(notADirectory, logger).send(message)).rejects.toThrow(EmailError);
  });
});

describe('SmtpEmailSender (real SMTP over TCP to a fake server)', () => {
  let smtp: FakeSmtpServer;

  beforeEach(async () => {
    smtp = await startFakeSmtpServer();
  });

  afterEach(async () => {
    await smtp.close();
  });

  const sender = () => SmtpEmailSender.fromConfig({ host: '127.0.0.1', port: smtp.port });

  it('delivers a plain-text message with From, To, Reply-To and Subject', async () => {
    await sender().send(message);

    expect(smtp.received).toHaveLength(1);
    const [mail] = smtp.received;
    expect(mail?.recipients).toEqual(['sales@example.com']);
    const headers = headerLines(mail!.raw);
    expect(headers).toEqual(
      expect.arrayContaining([
        'From: catalogue@example.com',
        'To: sales@example.com',
        'Reply-To: ada@example.com',
        'Subject: Enquiry: WD 4TB Gaming Drive (from Ada)',
      ]),
    );
    expect(headers.find((h) => h.startsWith('Content-Type:'))).toMatch(/^Content-Type: text\/plain/);
    expect(mail?.raw).not.toMatch(/text\/html/);
    expect(mail?.raw).toContain('Line one');
  });

  // Nodemailer itself replaces CR/LF in headers it encodes. This test pins that our
  // adapter keeps going through that encoding: it fails if the adapter bypasses it (e.g.
  // a `prepared` raw header) or moves to a client that doesn't. Our own explicit
  // defences are the HTTP 400 and the domain's toHeaderValue (enquiries.test.ts).
  it('header injection: CR/LF in the subject adds no Bcc header and no extra recipient', async () => {
    await sender().send({ ...message, subject: INJECTED_SUBJECT });

    const [mail] = smtp.received;
    expect(mail?.recipients).toEqual(['sales@example.com']);
    expect(headerLines(mail!.raw).some((line) => /^bcc:/i.test(line))).toBe(false);
  });

  it('turns an unreachable SMTP server into EmailError', async () => {
    await smtp.close();

    await expect(sender().send(message)).rejects.toThrow(EmailError);
  });
});

describe('createEmailSender: one config value picks the adapter', () => {
  const base = { salesInbox: 's@x.test', from: 'f@x.test' };

  it('EMAIL_TRANSPORT=file gives FileEmailSender', () => {
    const sender = createEmailSender({ ...base, transport: 'file', smtp: undefined }, '/tmp/x', new RecordingLogger());

    expect(sender).toBeInstanceOf(FileEmailSender);
  });

  it('EMAIL_TRANSPORT=smtp gives SmtpEmailSender', () => {
    const sender = createEmailSender(
      { ...base, transport: 'smtp', smtp: { host: 'mailpit', port: 1025 } },
      '/tmp/x',
      new RecordingLogger(),
    );

    expect(sender).toBeInstanceOf(SmtpEmailSender);
  });
});
