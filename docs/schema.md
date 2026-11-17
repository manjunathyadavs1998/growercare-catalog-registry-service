# catalog-registry-service database (`gc_catalog`, schema `catalog`)

One global catalog, not sharded (SPEC-CAT-01 D1). Roles: `gc_catalog_owner` (Flyway), `gc_catalog_app` (runtime), `gc_cdc` (Debezium: outbox and heartbeat only).

| Table | Key | Notes |
|---|---|---|
| `business_unit`, `country` | code | Reference rows (CP, SEEDS; IN, BR); read-only for the app role |
| `product` | `product_id` (sequence from 100000) | `product_code` UNIQUE (`^[A-Z0-9-]{3,32}$`), BU, name, kind, status ACTIVE / DISCONTINUED, source `version`. Index `(lower(name), product_id)` for search and keyset paging |
| `product_ingredient`, `product_pack` | (`product_id`, `position`) | Replaced on each product upsert (the only DELETE the app role has besides the outbox) |
| `registration` | (`country_code`, `product_id`) | Number, status REGISTERED / SUSPENDED / WITHDRAWN, `valid_until`, `version`. Search offers REGISTERED and not expired (D5) |
| `batch` | (`product_id`, `batch_no`) | mfg / expiry (expiry > mfg), plant, status RELEASED / BLOCKED / RECALLED, `version` |
| `outbox` | `event_id` | ADR-012, in publication `gc_outbox`; aggregate `product` / `batch` → topics `catalog.product.events` / `catalog.batch.events` |
| `cdc_heartbeat` | `id` = 1 | Debezium heartbeat (SPEC-PLAT-04 R62) |

App role: SELECT, INSERT, UPDATE on product, registration and batch; no DELETE or TRUNCATE (explicit REVOKEs, because the database's default privileges grant full DML).

## Migrations
- V1: everything above, the publication and the grants.
- Dev seed V1000 (local and tests only): products 9001–9004 with case-service's ids, their registrations and batches.
