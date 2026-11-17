import type { EmailSender } from '../ports/EmailSender';
import type { ProductSource } from '../ports/ProductSource';
import { NotFoundError } from './errors';
import type { Enquiry, Money, Product } from './types';

export interface EnquiryOptions {
  /** Where enquiries are sent (SALES_INBOX_EMAIL). */
  salesInbox: string;
  /** Our sending address (EMAIL_FROM). Never the customer's address. */
  from: string;
}

/** Enquiry use case: look up the product ourselves, build a plain-text email, send it. */
export class EnquiryService {
  constructor(
    private readonly products: ProductSource,
    private readonly email: EmailSender,
    private readonly options: EnquiryOptions,
  ) {}

  /**
   * Sends the enquiry to the sales inbox. Resolves once the email provider has
   * accepted it.
   *
   * @throws NotFoundError if the product does not exist. No email is sent.
   * @throws EmailError if the provider rejects it. The caller must not say "thank you".
   */
  async submit(enquiry: Enquiry): Promise<void> {
    // The product's title and price come from OUR source, never from the client.
    const product = await this.products.getProduct(enquiry.productId);
    if (!product) {
      throw new NotFoundError('product', enquiry.productId);
    }

    await this.email.send({
      to: this.options.salesInbox,
      from: this.options.from,
      replyTo: toHeaderValue(enquiry.email),
      subject: toHeaderValue(`Enquiry: ${product.title} (from ${enquiry.name})`),
      text: buildBody(product, enquiry),
    });
  }
}

/**
 * Makes a value safe to put in an email header by replacing CR and LF with spaces.
 *
 * Header injection: a header ends at CRLF, so a value like "Ann\r\nBcc: x@evil.com"
 * would otherwise add a Bcc header. The HTTP layer already rejects CR/LF in
 * single-line fields; this is the second, explicit line of defence at the point
 * where values become headers, so the domain is safe whoever calls it.
 */
export function toHeaderValue(value: string): string {
  return value.replace(/[\r\n]+/g, ' ');
}

function formatMoney({ amountMinor, currency }: Money): string {
  return `${currency} ${(amountMinor / 100).toFixed(2)}`;
}

// Plain text only: customer input goes into the body as-is, and plain text cannot
// carry HTML or script injection.
function buildBody(product: Product, enquiry: Enquiry): string {
  return [
    'New product enquiry',
    '',
    `Product:    ${product.title}`,
    `Product ID: ${product.id}`,
    `Price:      ${formatMoney(product.price)}`,
    '',
    `Name:       ${enquiry.name}`,
    `Email:      ${enquiry.email}`,
    `Phone:      ${enquiry.phone ?? '(not given)'}`,
    '',
    'Message:',
    enquiry.message ?? '(none)',
    '',
  ].join('\n');
}
