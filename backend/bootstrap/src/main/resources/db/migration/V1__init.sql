-- Inventory store. The search API reads only from `itinerary`; `provider_offer`
-- keeps per-provider provenance; `ingestion_run` records each run and its
-- per-provider outcome. The hot tables carry the run id but no FK, so bulk
-- upserts of up to 1M rows do not pay for constraint checks.

CREATE TABLE ingestion_run (
    id               UUID PRIMARY KEY,
    started_at       TIMESTAMPTZ NOT NULL,
    finished_at      TIMESTAMPTZ,
    status           VARCHAR(16) NOT NULL,            -- RUNNING | COMPLETED | PARTIAL | FAILED
    provider_results JSONB NOT NULL DEFAULT '{}'::jsonb -- per provider: status, latency_ms, rows, error
);

CREATE TABLE itinerary (
    id               BIGSERIAL PRIMARY KEY,
    signature        TEXT NOT NULL,                   -- deterministic key of the ordered segments
    origin           CHAR(3) NOT NULL,
    destination      CHAR(3) NOT NULL,
    carrier          VARCHAR(3) NOT NULL,             -- marketing carrier of the first segment
    departure_at     TIMESTAMPTZ NOT NULL,
    arrival_at       TIMESTAMPTZ NOT NULL,
    stops            SMALLINT NOT NULL,
    base_price       NUMERIC(12, 2) NOT NULL,         -- cheapest provider price
    price            NUMERIC(12, 2) NOT NULL,         -- base_price plus OTA markup
    currency         CHAR(3) NOT NULL,
    best_provider    VARCHAR(16) NOT NULL,
    segments         JSONB NOT NULL,
    ingestion_run_id UUID NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_itinerary_signature UNIQUE (signature)
);

-- Keyset pagination sorted by (price, id) under an origin/destination filter.
-- A max-price filter is a range on the same column, served by the same index.
CREATE INDEX ix_itinerary_route_price ON itinerary (origin, destination, price, id);

-- Same access path when the carrier filter is present.
CREATE INDEX ix_itinerary_route_carrier_price ON itinerary (origin, destination, carrier, price, id);

CREATE TABLE provider_offer (
    id               BIGSERIAL PRIMARY KEY,
    signature        TEXT NOT NULL,
    provider         VARCHAR(16) NOT NULL,
    price            NUMERIC(12, 2) NOT NULL,
    currency         CHAR(3) NOT NULL,
    fetched_at       TIMESTAMPTZ NOT NULL,
    ingestion_run_id UUID NOT NULL,
    CONSTRAINT uq_provider_offer UNIQUE (signature, provider)
);
