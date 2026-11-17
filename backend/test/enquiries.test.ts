import request from 'supertest';
import { beforeEach, describe, expect, it } from 'vitest';
import { API_PREFIX, buildApp } from '../src/app';
import { CatalogueService } from '../src/domain/catalogue';
import { EnquiryService } from '../src/domain/enquiries';
import { EmailError } from '../src/domain/errors';
import { enquiryBodySchema } from '../src/http/schemas';
import { type InMemoryProductSource, liveSnapshotSource } from './fakes/InMemoryProductSource';
import { RecordingEmailSender } from './fakes/RecordingEmailSender';
import { RecordingLogger } from './fakes/RecordingLogger';

const OPTIONS = { salesInbox: 'sales@example.com', from: 'catalogue@example.com' };
const ENQUIRIES = `${API_PREFIX}/enquiries`;

const validBody = {
  productId: '12',
  name: 'Ada Lovelace',
  email: 'ada@example.com',
  phone: '+44 7700 900123',
  message: 'Is this in stock?\nI need two.',
};

let source: InMemoryProductSource;
let logger: RecordingLogger;

beforeEach(async () => {
  source = await liveSnapshotSource();
  logger = new RecordingLogger();
});

function appWith(email: RecordingEmailSender) {
  return buildApp({
    catalogue: new CatalogueService(source, logger),
    enquiries: new EnquiryService(source, email, OPTIONS),
    logger,
  });
}

describe('POST /enquiries: success', () => {
  it('returns 200 {status: "sent"} and emails the sales inbox, replyTo the customer, from config', async () => {
    const email = new RecordingEmailSender();

    const res = await request(appWith(email)).post(ENQUIRIES).send(validBody);

    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: 'sent' });
    expect(email.sent).toHaveLength(1);
    const [sent] = email.sent;
    expect(sent).toMatchObject({ to: 'sales@example.com', from: 'catalogue@example.com', replyTo: 'ada@example.com' });
    expect(sent?.subject).toBe('Enquiry: WD 4TB Gaming Drive Works with Playstation 4 Portable External Hard Drive (from Ada Lovelace)');
    expect(sent?.text).toContain('Price:      USD 114.00');
    expect(sent?.text).toContain('Phone:      +44 7700 900123');
    // Line breaks are allowed in the message: it only goes in the body.
    expect(sent?.text).toContain('Is this in stock?\nI need two.');
  });

  it('uses the product title and price from OUR source, ignoring any the client sends', async () => {
    const email = new RecordingEmailSender();

    await request(appWith(email))
      .post(ENQUIRIES)
      .send({ ...validBody, title: 'FREE TELEVISION', price: 0, productTitle: 'FREE TELEVISION' });

    const [sent] = email.sent;
    expect(sent?.subject).toContain('WD 4TB Gaming Drive');
    expect(`${sent?.subject}${sent?.text}`).not.toContain('FREE TELEVISION');
    expect(sent?.text).toContain('USD 114.00');
    // Layer 1: the schema drops the client's product fields before the domain sees them.
    // (Layer 2 is structural: the domain only ever reads title/price from the source.)
    expect(enquiryBodySchema.parse({ ...validBody, title: 'FREE TELEVISION', price: 0 })).not.toHaveProperty('title');
  });

  it('accepts an enquiry with only the required fields', async () => {
    const email = new RecordingEmailSender();

    const res = await request(appWith(email)).post(ENQUIRIES).send({ productId: '1', name: 'Ada', email: 'ada@example.com', phone: '' });

    expect(res.status).toBe(200);
    expect(email.sent[0]?.text).toContain('Phone:      (not given)');
  });
});

describe('POST /enquiries: product lookup', () => {
  it('returns 404 NOT_FOUND for an unknown productId, and sends NO email', async () => {
    const email = new RecordingEmailSender();

    const res = await request(appWith(email)).post(ENQUIRIES).send({ ...validBody, productId: '999' });

    expect(res.status).toBe(404);
    expect(res.body).toEqual({ error: { code: 'NOT_FOUND', message: 'The product was not found' } });
    expect(email.sent).toEqual([]);
  });
});

describe('POST /enquiries: validation (400, nothing sent)', () => {
  it.each([
    ['name', 'missing', { name: undefined }],
    ['name', 'blank', { name: '   ' }],
    ['name', 'over 100 characters', { name: 'a'.repeat(101) }],
    ['email', 'invalid', { email: 'not-an-email' }],
    ['phone', 'letters', { phone: 'call me maybe' }],
    ['message', 'over 1000 characters', { message: 'a'.repeat(1001) }],
    ['productId', 'missing', { productId: undefined }],
  ])('rejects %s when %s', async (field, _label, override) => {
    const email = new RecordingEmailSender();

    const res = await request(appWith(email)).post(ENQUIRIES).send({ ...validBody, ...override });

    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
    expect(res.body.error.details.map((d: { field: string }) => d.field)).toContain(field);
    expect(email.sent).toEqual([]);
  });

  it('rejects a request with no JSON body, naming "body"', async () => {
    const res = await request(appWith(new RecordingEmailSender())).post(ENQUIRIES);

    expect(res.status).toBe(400);
    expect(res.body.error.details[0].field).toBe('body');
  });
});

describe('POST /enquiries: header injection', () => {
  it('rejects a name containing "\\r\\nBcc: ..." with 400, and sends nothing', async () => {
    const email = new RecordingEmailSender();

    const res = await request(appWith(email))
      .post(ENQUIRIES)
      .send({ ...validBody, name: 'Ada\r\nBcc: someone@evil.com' });

    expect(res.status).toBe(400);
    expect(res.body.error.details[0].field).toBe('name');
    expect(email.sent).toEqual([]);
  });

  it('rejects an email containing CR/LF', async () => {
    const res = await request(appWith(new RecordingEmailSender()))
      .post(ENQUIRIES)
      .send({ ...validBody, email: 'ada@example.com\r\nBcc: someone@evil.com' });

    expect(res.status).toBe(400);
    expect(res.body.error.details[0].field).toBe('email');
  });

  it('domain defence in depth: even called directly, CR/LF never reaches the subject or replyTo', async () => {
    const email = new RecordingEmailSender();
    const service = new EnquiryService(source, email, OPTIONS);

    await service.submit({ productId: '12', name: 'Ada\r\nBcc: someone@evil.com', email: 'ada@example.com\nX: y' });

    const [sent] = email.sent;
    expect(sent?.subject).not.toMatch(/[\r\n]/);
    expect(sent?.replyTo).not.toMatch(/[\r\n]/);
  });
});

describe('POST /enquiries: email failure', () => {
  it('returns 502 EMAIL_FAILED (never 200), so the client cannot show "Thank you"', async () => {
    const email = new RecordingEmailSender(new EmailError('SMTP server did not accept the enquiry email'));

    const res = await request(appWith(email)).post(ENQUIRIES).send(validBody);

    expect(res.status).toBe(502);
    expect(res.body).toEqual({
      error: { code: 'EMAIL_FAILED', message: 'Your enquiry could not be sent. Please try again.' },
    });
    expect(res.body).not.toHaveProperty('status');
    expect(logger.warnings[0]?.message).toBe('Email delivery failure');
  });
});
