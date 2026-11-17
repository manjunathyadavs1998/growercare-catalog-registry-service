# growercare-catalog-registry-service

Product master for GrowerCare: products, registrations per country and batches, searchable by users and fed by an ERP / regulatory sync. One of the independent service repositories of the GrowerCare platform (ADR-017).

- **Spec:** [`docs/specs/catalog/01-product-registry.md`](docs/specs/catalog/01-product-registry.md)
- **Port:** 8086 · **Image:** `ghcr.io/manjunathyadavs1998/catalog-registry-service`
- **Configuration:** environment variables only, see [`.env.example`](.env.example)

## Build and run
```bash
mvn -B verify                                   # tests (Docker for Testcontainers), coverage gate input
cp .env.example .env && mvn -B package jib:dockerBuild -DskipTests && docker compose up -d
```
The shared infrastructure (Postgres, Redis, Keycloak, Kafka) comes from `growercare-local-platform`.
