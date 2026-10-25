--liquibase formatted sql

--changeset ektrepha:46
-- GST on the care amount itself (subtotal_paise), not just the platform fee - business-confirmed,
-- 2026, at the same rate as platform_fee_gst_paise (app.pricing.platform-fee-gst-pct). Every
-- existing GST column here was scoped to the platform fee alone (see migration 044's comment) -
-- this adds the missing care-amount side so a booking/quote/payment's cgst/sgst/igst split now
-- covers ALL the GST it carries, not only the platform fee's share.
--
-- hourly_care_quote gets its own copy (the locked-quote row Checkout pays against); booking and
-- payment_transaction follow the same "own copy at creation/initiate time" pattern migration 044
-- already established, for the same reason - a self-contained record even if pricing rules move
-- before a later attempt.
ALTER TABLE hourly_care_quote ADD COLUMN care_gst_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE booking ADD COLUMN care_gst_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payment_transaction ADD COLUMN care_gst_paise BIGINT NOT NULL DEFAULT 0;
