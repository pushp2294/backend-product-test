// Thin HTTP layer: validate input, call the domain, map the result to our response
// types. No business rules here.
//
// No try/catch: Express 5 forwards errors thrown in async handlers to the error
// middleware (errorHandler.ts), which owns every error response.
import { Router } from 'express';
import type { CatalogueService } from '../domain/catalogue';
import type { EnquiryService } from '../domain/enquiries';
import {
  categoryParamsSchema,
  enquiryBodySchema,
  productParamsSchema,
  searchQuerySchema,
  toCategoryProductsResponse,
  toProductDetail,
  toProductSummary,
} from './schemas';

export function createRouter(catalogue: CatalogueService, enquiries: EnquiryService): Router {
  const router = Router();

  // Liveness only: answers without touching the product source, so it stays fast
  // and is not affected by upstream outages.
  router.get('/health', (_req, res) => {
    res.json({ status: 'ok' });
  });

  // Registered BEFORE /products/:id, otherwise Express would treat "featured" as an id.
  router.get('/products/featured', async (_req, res) => {
    const products = await catalogue.getFeatured();
    res.json({ products: products.map(toProductSummary) });
  });

  router.get('/products/:id', async (req, res) => {
    // .parse throws a ZodError on bad input; the error handler turns it into a 400.
    const { id } = productParamsSchema.parse(req.params);
    const product = await catalogue.getProduct(id);
    res.json({ product: toProductDetail(product) });
  });

  router.get('/categories', async (_req, res) => {
    const categories = await catalogue.listCategories();
    res.json({ categories });
  });

  router.get('/categories/:categoryId/products', async (req, res) => {
    const { categoryId } = categoryParamsSchema.parse(req.params);
    const result = await catalogue.getCategoryProducts(categoryId);
    res.json(toCategoryProductsResponse(result));
  });

  router.get('/search', async (req, res) => {
    const { q } = searchQuerySchema.parse(req.query);
    const products = await catalogue.search(q);
    res.json({ query: q, products: products.map(toProductSummary) });
  });

  // 200 only once the email provider has accepted the message. Any failure becomes an
  // error response, so the client never shows "Thank you" for an email that was not sent.
  router.post('/enquiries', async (req, res) => {
    const enquiry = enquiryBodySchema.parse(req.body);
    await enquiries.submit(enquiry);
    res.json({ status: 'sent' });
  });

  return router;
}
