-- =====================================================================================================
-- catalog-registry-service · V1 · products, registrations, batches, outbox (SPEC-CAT-01 R30)
-- One global catalog, not sharded (D1). Placeholders: ${app_role}, ${cdc_role}
-- =====================================================================================================
GRANT USAGE ON SCHEMA catalog TO ${app_role};

-- Reference rows (codes shared with IAM and case-service).
CREATE TABLE catalog.business_unit (
    bu_code varchar(8)  PRIMARY KEY,
    name    varchar(60) NOT NULL
);
INSERT INTO catalog.business_unit (bu_code, name) VALUES ('CP', 'Crop Protection'), ('SEEDS', 'Seeds');

CREATE TABLE catalog.country (
    country_code char(2)     PRIMARY KEY CHECK (country_code ~ '^[A-Z]{2}$'),
    name         varchar(60) NOT NULL
);
INSERT INTO catalog.country (country_code, name) VALUES ('IN', 'India'), ('BR', 'Brazil');

-- D3: ids from 100000; the dev seed keeps case-service's 9001-9004.
CREATE SEQUENCE catalog.product_id_seq START WITH 100000;

CREATE TABLE catalog.product (
    product_id   bigint       NOT NULL DEFAULT nextval('catalog.product_id_seq'),
    product_code varchar(32)  NOT NULL,
    bu_code      varchar(8)   NOT NULL REFERENCES catalog.business_unit (bu_code),
    name         varchar(120) NOT NULL,
    product_kind varchar(24)  NOT NULL,
    status       varchar(16)  NOT NULL,
    version      bigint       NOT NULL,
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT product_pk PRIMARY KEY (product_id),
    CONSTRAINT product_code_uq UNIQUE (product_code),
    CONSTRAINT product_code_check CHECK (product_code ~ '^[A-Z0-9-]{3,32}$'),
    CONSTRAINT product_kind_check CHECK (product_kind IN ('FUNGICIDE', 'INSECTICIDE', 'HERBICIDE', 'SEED', 'BIOLOGICAL')),
    CONSTRAINT product_status_check CHECK (status IN ('ACTIVE', 'DISCONTINUED')),
    CONSTRAINT product_name_check CHECK (btrim(name) <> '')
);
-- D5: name search (case-insensitive contains) on a small table; a trigram index comes with growth.
CREATE INDEX product_name_ix ON catalog.product (lower(name), product_id);

CREATE TABLE catalog.product_ingredient (
    product_id    bigint       NOT NULL REFERENCES catalog.product (product_id),
    position      smallint     NOT NULL,
    name          varchar(120) NOT NULL,
    concentration varchar(40)  NOT NULL,
    PRIMARY KEY (product_id, position)
);

CREATE TABLE catalog.product_pack (
    product_id bigint      NOT NULL REFERENCES catalog.product (product_id),
    position   smallint    NOT NULL,
    pack_size  varchar(40) NOT NULL,
    PRIMARY KEY (product_id, position)
);

CREATE TABLE catalog.registration (
    country_code    char(2)     NOT NULL REFERENCES catalog.country (country_code),
    product_id      bigint      NOT NULL REFERENCES catalog.product (product_id),
    registration_no varchar(40) NOT NULL,
    status          varchar(16) NOT NULL CHECK (status IN ('REGISTERED', 'SUSPENDED', 'WITHDRAWN')),
    valid_until     date,
    version         bigint      NOT NULL,
    updated_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (country_code, product_id)
);
CREATE INDEX registration_product_ix ON catalog.registration (product_id);

CREATE TABLE catalog.batch (
    product_id  bigint      NOT NULL REFERENCES catalog.product (product_id),
    batch_no    varchar(32) NOT NULL CHECK (batch_no ~ '^[A-Z0-9-]{4,32}$'),
    mfg_date    date        NOT NULL,
    expiry_date date        NOT NULL,
    plant_code  varchar(16) NOT NULL,
    status      varchar(16) NOT NULL CHECK (status IN ('RELEASED', 'BLOCKED', 'RECALLED')),
    version     bigint      NOT NULL,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, batch_no),
    CONSTRAINT batch_dates_check CHECK (expiry_date > mfg_date)
);

-- ADR-012 outbox, ids only (D7); Debezium routes by aggregate_type to catalog.<aggregate>.events.
CREATE TABLE catalog.outbox (
    event_id       uuid         PRIMARY KEY,
    aggregate_type varchar(64)  NOT NULL,
    aggregate_id   varchar(64)  NOT NULL,
    event_type     varchar(128) NOT NULL,
    payload        jsonb        NOT NULL,
    occurred_at    timestamptz  NOT NULL DEFAULT now(),
    trace_id       varchar(32)
);
CREATE INDEX outbox_occurred_ix ON catalog.outbox (occurred_at);

-- SPEC-PLAT-04 R62: Debezium upserts this row every 10 s so the replication slot advances without events.
CREATE TABLE catalog.cdc_heartbeat (
    id      smallint    PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    beat_at timestamptz NOT NULL
);
CREATE PUBLICATION gc_outbox FOR TABLE catalog.outbox, catalog.cdc_heartbeat WITH (publish = 'insert, update');
GRANT USAGE ON SCHEMA catalog TO ${cdc_role};
GRANT SELECT ON catalog.outbox TO ${cdc_role};
GRANT SELECT, INSERT, UPDATE ON catalog.cdc_heartbeat TO ${cdc_role};

-- Runtime role (R30): upserts only, no DELETE except ingredient/pack lists (replaced on each product upsert) and
-- outbox housekeeping. Explicit REVOKEs: the databases grant full DML on new tables by default.
GRANT SELECT ON catalog.business_unit, catalog.country TO ${app_role};
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON catalog.business_unit, catalog.country FROM ${app_role};
GRANT SELECT, INSERT, UPDATE ON catalog.product, catalog.registration, catalog.batch TO ${app_role};
REVOKE DELETE, TRUNCATE ON catalog.product, catalog.registration, catalog.batch FROM ${app_role};
GRANT SELECT, INSERT, DELETE ON catalog.product_ingredient, catalog.product_pack TO ${app_role};
REVOKE UPDATE, TRUNCATE ON catalog.product_ingredient, catalog.product_pack FROM ${app_role};
GRANT SELECT, INSERT, DELETE ON catalog.outbox TO ${app_role};
REVOKE UPDATE, TRUNCATE ON catalog.outbox FROM ${app_role};
REVOKE ALL ON catalog.cdc_heartbeat FROM ${app_role};
GRANT USAGE ON SEQUENCE catalog.product_id_seq TO ${app_role};
