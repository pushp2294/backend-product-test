# Dated snapshot of the Fake Store API

These files are **real responses captured from the live Fake Store API**, saved
byte-for-byte. Nothing in them was invented or edited. They are snapshots of a source
we do not control, so the live API may have changed since.

They have two uses, from this one copy:
- **Production, opt-in:** `PRODUCT_SOURCE=snapshot` makes the backend serve these files
  through `SnapshotProductSource` instead of calling the live API (for offline review, or
  when Fake Store is slow or down).
- **Tests:** adapter and domain tests run against exactly this data.

| File | Source URL | Captured | SHA-256 |
|---|---|---|---|
| `fakestore-products.json` | `https://fakestoreapi.com/products` | 2026-11-16 | `8a244bc1211c3cd0a3ee03e8e72b259c2dfc6a2d8c1d355169c67eb2caf4c2e7` |
| `fakestore-categories.json` | `https://fakestoreapi.com/products/categories` | 2026-11-17 | `eec30cab912fc29d24465f1a24318171baa1567c10625b62adb9d392586e3948` |

Captured with:

```sh
curl -sS https://fakestoreapi.com/products -o fakestore-products.json
curl -sS https://fakestoreapi.com/products/categories -o fakestore-categories.json
```

What these snapshots showed (and the tests now pin):
- 20 products: men's clothing 4, jewelery 4, electronics 6, women's clothing 6.
- `id`, `price` and `rating.rate` are JSON numbers; no price has more than 2 decimals.
- Top 5 by rate desc, then count desc, then id: **12, 11, 3, 18, 5**.

Behaviour observed on the same day but not stored as a file (there is no body to store):
- `GET /products/999` returns **HTTP 200 with an empty body**, not 404.
