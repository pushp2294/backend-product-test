# Contract fixtures: real responses from our backend

Captured on 2026-11-17 from the backend running locally (`npm run dev`, file email
transport, no FEATURED_PRODUCT_IDS), byte-for-byte via curl against `http://localhost:3000/api/v1`.
Nothing here was hand-written. Client tests decode these with the app's real DTOs and Json
settings, so a field-name mismatch between client and server fails a test, not the app.

| File | Request |
|---|---|
| featured.json | GET /products/featured |
| categories.json | GET /categories |
| category-electronics.json | GET /categories/electronics/products |
| search-men.json | GET /search?q=men |
| product-12.json | GET /products/12 |
| error-not-found.json | GET /products/999 (404) |
| error-validation.json | GET /search?q= (400) |
| error-enquiry-validation.json | POST /enquiries with empty name and bad email (400) |
