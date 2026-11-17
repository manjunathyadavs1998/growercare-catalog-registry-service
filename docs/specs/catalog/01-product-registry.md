# SPEC-CAT-01: Products, registrations and batches
- **Requirements:** GRW-2 (product master), GRW-3 (registration per country; label detail later), GRW-5 (batch/lot master), Phase 1 PRD journey **J4** and M2
- **Status:** Agreed
- **Owner:** catalog-registry-service (new repository `growercare-catalog-registry-service`, port 8086); case-service replicates what it needs (SPEC-CASE-08)
- **Not in Phase 1:** label rows (crop × pest × dose × PHI) and the label-compliance check GRW-4; recalls GRW-6

## 0. Decisions (to agree with this spec)
| # | Decision | Why |
|---|---|---|
| D1 | **One global catalog database `gc_catalog`, not sharded**, schema `catalog` | Reference data: thousands of products, registrations per country, batches in the low millions. Partition batches by product later if needed |
| D2 | **The catalog is fed by an import API, not edited by people in Phase 1:** `PUT /internal/products/{productCode}`, `PUT /internal/products/{productCode}/registrations/{country}`, `PUT /internal/products/{productCode}/batches/{batchNo}` for allow-listed clients (`erp-sync`). Idempotent upserts with a source `version` (stale versions ignored). An admin UI is Phase 2 | GRW-2/5 say "synced from regulatory/ERP"; upserts by business key make the sync safe to replay |
| D3 | **Product ids are catalog-generated `bigint`s** (sequence from 100000); the dev seed keeps case-service's 9001–9003 | Existing local cases reference 9001–9003 (`cases_product_fk`) |
| D4 | **Reading the catalog needs an authenticated GrowerCare user, no extra permission.** Searches always name a country; growers and partners may only search their home country; staff their home country (where territory and assigned grants lie, which name no country) plus every country a current grant names, all countries with a GLOBAL grant (otherwise 404 on that country) | Product data is not personal, but registrations are per country and the UI should only offer what applies (PRD J4: "only products registered in India") |
| D5 | **Search = registered products only:** `GET /products?country=IN&q=…` returns products with registration status `REGISTERED` and `valid_until` not passed in that country, matching name or code (case-insensitive prefix/contains), 20 per page | J4: the grower picks from a searchable list of what is allowed |
| D6 | **Batches are validated, not listed:** `GET /products/{id}/batches/{batchNo}` → 200 or 404. There is no "list all batches" for users | A batch list would let anyone enumerate production; J4 only needs "is this batch real for this product?" |
| D7 | **Events (ids only) on `catalog.product.events` and `catalog.batch.events`:** `catalog.product.upserted {productId, version}`, `catalog.registration.changed {productId, countryCode, status}`, `catalog.batch.upserted {productId, batchNo, version}`. Consumers fetch details from the internal views | Same outbox → Debezium pattern as the other services |
| D8 | **Batch status** `RELEASED` / `BLOCKED` / `RECALLED`; a complaint may name any of them (a recalled batch is exactly what growers complain about) | Status matters for GRW-6 recalls later, not for accepting a complaint |

## 1. Behaviour (rules)
### Reads (users)
| Rule | Given | When | Then |
|---|---|---|---|
| CAT-R1 | authenticated user, country allowed for them (D4) | `GET /products?country=IN&q=azo[&kind=FUNGICIDE][&cursor]` | 200 `{items, nextCursor}`, registered products only (D5), ordered by name; `q` shorter than 2 characters lists all registered products |
| CAT-R2 | a country the caller may not see · unknown country | search | 404 |
| CAT-R3 | the caller | `GET /products/{productId}?country=IN` | 200 product with that country's registration; 404 when unknown or not registered there |
| CAT-R4 | the caller | `GET /products/{productId}/batches/{batchNo}` | 200 `{productId, batchNo, mfgDate, expiryDate, status}`; 404 when the batch is not of that product (D6) |
| CAT-R5 | no token · garbage token | any | 401 |

### Import (ERP / regulatory sync)
| Rule | Given | When | Then |
|---|---|---|---|
| CAT-R10 | client in `GC_INTERNAL_CLIENTS` (`erp-sync`) | `PUT /internal/products/{productCode}` `{buCode, name, kind, activeIngredients[{name, concentration}], packSizes[], status, version}` | 201 new (id assigned) · 200 updated; event `catalog.product.upserted` |
| CAT-R11 | same, `version` ≤ stored version | upsert | 200 unchanged, no event (replayed or stale sync) |
| CAT-R12 | same | `PUT …/registrations/{country}` `{registrationNo, status REGISTERED/SUSPENDED/WITHDRAWN, validUntil?, version}` | 200; event `catalog.registration.changed` when the status or validity changed |
| CAT-R13 | same | `PUT …/batches/{batchNo}` `{mfgDate, expiryDate, plantCode, status, version}` | 201/200; event `catalog.batch.upserted` |
| CAT-R14 | invalid input: unknown BU or kind, product code not `^[A-Z0-9-]{3,32}$`, batch no not `^[A-Z0-9-]{4,32}$`, expiry ≤ mfg, unknown country, missing version | upsert | 400 naming the field |
| CAT-R15 | registration or batch for an unknown product code | upsert | 404 |
| CAT-R16 | a user token · client not allow-listed | import | 403 · 401 |

### Internal views (case-service)
| Rule | Given | When | Then |
|---|---|---|---|
| CAT-R20 | allow-listed client (`case-service`) | `GET /internal/products/{productId}` | 200 `{productId, productCode, buCode, name, kind, status, version, registrations: [{countryCode, status, validUntil}]}` |
| CAT-R21 | same | `GET /internal/products/{productId}/batches/{batchNo}` | 200 batch view with version; 404 unknown |

### Data and seed
| Rule | Given | When | Then |
|---|---|---|---|
| CAT-R30 | the database | migration | `product`, `product_ingredient`, `product_pack`, `registration`, `batch`, `outbox` (in publication `gc_outbox`), `business_unit` and `country` reference rows; app role: no DELETE on catalog tables |
| CAT-R31 | dev seed | migration | products 9001 Azoxa Duo 325 SC (CP, FUNGICIDE), 9002 Lambdor 5 CS (CP, INSECTICIDE), 9003 Hybrid corn HC-6668 (SEEDS, SEED), plus 9004 Mancozan 75 WP (CP, FUNGICIDE, registration IN **WITHDRAWN**); registrations and batches as case-service's seed (`AZX2604-117`, `LMB2602-031`, `HYC2605-009`) |

## 2. Examples
| Example | Rule | Actor | Input | Expected |
|---|---|---|---|---|
| CAT-E1 | R1 | grower.in1 | `country=IN&q=azo` | Azoxa Duo 325 SC only |
| CAT-E2 | R1 | grower.in1 | `country=IN` (no q) | 9001, 9002, 9003; not 9004 (withdrawn) |
| CAT-E3 | R1 | grower.br1 | `country=BR` | 9001 only |
| CAT-E4 | R2 | grower.in1 · care.br · care.in | `country=BR` · `country=IN` · `country=XX` | 404 · 404 · 404 |
| CAT-E5 | R3 | grower.in1 | product 9001 · 9003 with `country=BR` · 999999 | 200 · 404 · 404 |
| CAT-E6 | R4 | grower.in1 | 9001 / `AZX2604-117` · 9001 / `HYC2605-009` (batch of 9003) · 9001 / `NOPE-1` | 200 · 404 · 404 |
| CAT-E7 | R5 | — | no token · garbage | 401 · 401 |
| CAT-E10 | R10 | erp-sync | new code `TST-1`, version 1 · same code, new name, version 2 | 201 with a new id ≥ 100000, event · 200, event |
| CAT-E11 | R11 | erp-sync | `TST-1` version 2 again · version 1 | 200 unchanged, no event (both) |
| CAT-E12 | R12 | erp-sync | `TST-1` registration IN REGISTERED · then SUSPENDED | 200 + event · 200 + event; then search IN no longer lists it |
| CAT-E13 | R13 | erp-sync | batch `TST1-0001` for `TST-1` · update its status to RECALLED | 201 + event · 200 + event |
| CAT-E14 | R14 | erp-sync | BU `XX` · kind `PAINT` · code `t1` · batch `ab` · expiry before mfg · country `XX` · no version | 400 naming `buCode` / `kind` / `productCode` / `batchNo` / `expiryDate` / `country` / `version` |
| CAT-E15 | R15 | erp-sync | registration for code `NOPE` | 404 |
| CAT-E16 | R16 | care.in user · `integration-service` client | import | 403 · 401 |
| CAT-E20 | R20 | case-service | `/internal/products/9001` | 200 with IN and BR registrations |
| CAT-E21 | R21 | case-service | `/internal/products/9001/batches/AZX2604-117` · unknown | 200 · 404 |
| CAT-E30 | R30 | app role | `DELETE FROM catalog.product` | permission denied |
| CAT-E31 | R31 | dev seed | 9001–9004 | as listed |

## 3. API contract
```
GET /products?country=&q=&kind=&cursor=             200 {items: [ProductSummary], nextCursor?}
GET /products/{productId}?country=                  200 Product
GET /products/{productId}/batches/{batchNo}         200 Batch
PUT /internal/products/{productCode}                200|201 Product      (erp-sync)
PUT /internal/products/{productCode}/registrations/{countryCode}   200   (erp-sync)
PUT /internal/products/{productCode}/batches/{batchNo}            200|201 (erp-sync)
GET /internal/products/{productId}                  200 ProductReplica   (case-service)
GET /internal/products/{productId}/batches/{batchNo} 200 BatchReplica    (case-service)
```
Ids are strings. Errors are Problem Details as everywhere.

## 4. Data & events
- `gc_catalog`, schema `catalog`: `product` (id, code unique, bu, name, kind, status, version), `product_ingredient`, `product_pack`, `registration` (country, product, number, status, valid_until, version), `batch` (product, batch_no, mfg, expiry, plant, status, version), `outbox`; reference copies `business_unit`, `country`.
- Outbox aggregate `product` / `batch` → topics `catalog.product.events`, `catalog.batch.events` (new Debezium connector `catalog-outbox`).
- Principals from IAM (client credentials, Redis cache) like the other services.

## 5. Security
| Concern | Rule |
|---|---|
| Users | authenticated GrowerCare user; country limited by D4 (404) |
| Import | allow-listed service clients only (R16) |
| Enumeration | no batch listing (D6) |

## 6. Traceability
| Rule / Example | Test |
|---|---|
| R1–R5 / E1–E7 | `ProductSearchIT` (incl. keyset paging, literal wildcards), `CountryAccessTest` (country rule) |
| §3 contract | `CatalogOpenApiContractIT` |
| R10–R16 / E10–E16 | `CatalogImportIT` |
| R20–R21 / E20–E21 | `InternalCatalogIT` |
| R30–R31 / E30–E31 | `CatalogSchemaMigrationIT` |
