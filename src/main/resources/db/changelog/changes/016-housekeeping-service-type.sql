--liquibase formatted sql

--changeset ektrepha:16
-- "Housekeeping" has been a clickable category on the homepage since early on, but it was never
-- added to service_types, so every /serviceability/search result silently omitted it (no card,
-- no "coming soon" badge, nothing) instead of showing it like the other not-yet-launched
-- categories (Adult Care, Pet Care, Tutoring). Adding it here is enough on its own — the
-- serviceability matrix defaults any service type with no serviceability_service_type row to
-- NOT_PLANNED, so it will now show up honestly as "Not in this area" everywhere until it's
-- deliberately rolled out to a zone, matching the existing pattern.

INSERT INTO service_types (code, name, pricing_unit)
VALUES ('housekeeping', 'Housekeeping', 'hourly')
ON CONFLICT (code) DO NOTHING;
