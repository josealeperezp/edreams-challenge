-- Provider offers become an insert-only snapshot per run: unique per (run, signature, provider),
-- idempotent on retry, purged by run. Segments are stored so the resolve phase rebuilds the
-- winning itinerary without a second provider call.

ALTER TABLE provider_offer DROP CONSTRAINT uq_provider_offer;
ALTER TABLE provider_offer
    ADD COLUMN provider_offer_id VARCHAR(64),
    ADD COLUMN segments JSONB NOT NULL DEFAULT '[]'::jsonb;
-- The unique index is also the access path of "ORDER BY signature, provider" within one run.
ALTER TABLE provider_offer ADD CONSTRAINT uq_provider_offer_run UNIQUE (ingestion_run_id, signature, provider);

-- Every provider's quote next to the winner, so the API can show why a price won.
ALTER TABLE itinerary ADD COLUMN quotes JSONB NOT NULL DEFAULT '[]'::jsonb;
-- Purge of rows no longer confirmed by a recent run.
CREATE INDEX ix_itinerary_run ON itinerary (ingestion_run_id);
-- Keyset order when no route filter is present.
CREATE INDEX ix_itinerary_price ON itinerary (price, id);

ALTER TABLE ingestion_run
    ADD COLUMN itineraries INTEGER,
    ADD COLUMN error TEXT;
