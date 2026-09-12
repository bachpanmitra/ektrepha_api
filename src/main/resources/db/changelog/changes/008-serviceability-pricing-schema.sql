--liquibase formatted sql

--changeset bachpanmitra:8
-- Serviceability & Pricing Engine: zones, per-zone-per-service pricing (fixed or range) with
-- day-type surge rules, pincode/service-type availability, geocode cache, waitlist, caregiver
-- (nanny) zone mapping for the marketplace rate model, and demand-based dynamic pricing.
--
-- Two deliberate deviations from the original design doc:
-- 1. zone_areas.boundary (PostGIS GEOGRAPHY polygon) is dropped. This codebase already has a
--    working geo story on cube/earthdistance (see migration 006's idx_parent_address_geo /
--    idx_nanny_service_area_geo) and nothing in the API spec needs point-in-polygon containment
--    - nearest-zone-by-centroid via ll_to_earth covers the lat/lng search case without adding a
--    new PostGIS extension dependency.
-- 2. All ids are BIGSERIAL/BIGINT (not SERIAL/INTEGER as drafted) to match this codebase's
--    bigint-ids convention (migration 005) and so caregiver_zone_mapping.caregiver_id can carry
--    a real FK to nanny(id) instead of a bare untyped integer.
--
-- Enum-like columns stay VARCHAR with a CHECK constraint rather than this codebase's other
-- convention of SMALLINT codes (migration 006) - the design doc calls out these exact strings
-- ('fixed'/'range', 'weekday'/'weekend'/'holiday', etc.) as part of the wire contract, so keeping
-- them literal avoids a translation layer between the DB and the API responses.

CREATE TABLE service_types (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(30) UNIQUE NOT NULL,
    name         VARCHAR(100) NOT NULL,
    pricing_unit VARCHAR(20) NOT NULL CHECK (pricing_unit IN ('hourly', 'per_visit', 'per_session', 'monthly')),
    is_active    BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE zone_areas (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    city         VARCHAR(100) NOT NULL,
    state        VARCHAR(100) NOT NULL,
    centroid_lat DOUBLE PRECISION,
    centroid_lng DOUBLE PRECISION,
    is_active    BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE zone_service_pricing (
    id                BIGSERIAL PRIMARY KEY,
    zone_area_id      BIGINT NOT NULL REFERENCES zone_areas(id),
    service_type_id   BIGINT NOT NULL REFERENCES service_types(id),
    pricing_mode      VARCHAR(10) NOT NULL CHECK (pricing_mode IN ('fixed', 'range')),
    fix_price         NUMERIC(10, 2),
    rate_min          NUMERIC(10, 2),
    rate_max          NUMERIC(10, 2),
    unit_price        NUMERIC(10, 2),
    currency          VARCHAR(3) NOT NULL DEFAULT 'INR',
    min_booking_hours NUMERIC(4, 2) NOT NULL DEFAULT 1,
    platform_fee_pct  NUMERIC(5, 2) NOT NULL DEFAULT 0,
    is_active         BOOLEAN NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (zone_area_id, service_type_id),
    CONSTRAINT chk_zsp_fixed_price CHECK (pricing_mode <> 'fixed' OR fix_price IS NOT NULL),
    CONSTRAINT chk_zsp_range_price CHECK (pricing_mode <> 'range' OR (rate_min IS NOT NULL AND rate_max IS NOT NULL AND rate_min <= rate_max))
);

CREATE TABLE zone_pricing_rules (
    id                       BIGSERIAL PRIMARY KEY,
    zone_service_pricing_id BIGINT NOT NULL REFERENCES zone_service_pricing(id) ON DELETE CASCADE,
    day_type                 VARCHAR(10) NOT NULL CHECK (day_type IN ('weekday', 'weekend', 'holiday')),
    start_time                TIME NOT NULL,
    end_time                  TIME NOT NULL,
    price_multiplier          NUMERIC(4, 2) NOT NULL DEFAULT 1.0,
    adjusted_fix_price        NUMERIC(10, 2),
    priority                  INT NOT NULL DEFAULT 0,
    is_active                 BOOLEAN NOT NULL DEFAULT true,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_rule_window CHECK (end_time > start_time)
);

CREATE TABLE serviceability_pincode (
    id             BIGSERIAL PRIMARY KEY,
    pincode        VARCHAR(6) NOT NULL UNIQUE,
    zone_area_id   BIGINT NOT NULL REFERENCES zone_areas(id),
    is_serviceable BOOLEAN NOT NULL DEFAULT true,
    status         VARCHAR(20) NOT NULL DEFAULT 'live' CHECK (status IN ('live', 'coming_soon', 'not_planned')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE serviceability_service_type (
    id              BIGSERIAL PRIMARY KEY,
    zone_area_id    BIGINT NOT NULL REFERENCES zone_areas(id),
    service_type_id BIGINT NOT NULL REFERENCES service_types(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'not_planned' CHECK (status IN ('live', 'coming_soon', 'not_planned')),
    launched_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (zone_area_id, service_type_id)
);

CREATE TABLE geocode_cache (
    id                BIGSERIAL PRIMARY KEY,
    query_text        VARCHAR(255) NOT NULL,
    normalized_query  VARCHAR(255) NOT NULL UNIQUE,
    lat               DOUBLE PRECISION,
    lng               DOUBLE PRECISION,
    formatted_address VARCHAR(500),
    provider          VARCHAR(30),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE serviceability_waitlist (
    id              BIGSERIAL PRIMARY KEY,
    pincode         VARCHAR(6),
    zone_area_id    BIGINT REFERENCES zone_areas(id),
    service_type_id BIGINT REFERENCES service_types(id),
    contact         VARCHAR(255) NOT NULL,
    notified_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE caregiver_zone_mapping (
    id              BIGSERIAL PRIMARY KEY,
    caregiver_id    BIGINT NOT NULL REFERENCES nanny(id) ON DELETE CASCADE,
    zone_area_id    BIGINT NOT NULL REFERENCES zone_areas(id),
    service_type_id BIGINT NOT NULL REFERENCES service_types(id),
    own_rate        NUMERIC(10, 2),
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (caregiver_id, zone_area_id, service_type_id)
);

CREATE TABLE dynamic_pricing_config (
    id                      BIGSERIAL PRIMARY KEY,
    zone_service_pricing_id BIGINT NOT NULL UNIQUE REFERENCES zone_service_pricing(id) ON DELETE CASCADE,
    is_enabled              BOOLEAN NOT NULL DEFAULT false,
    demand_threshold_low    NUMERIC(4, 2) NOT NULL DEFAULT 0.5,
    demand_threshold_high   NUMERIC(4, 2) NOT NULL DEFAULT 1.5,
    min_multiplier          NUMERIC(4, 2) NOT NULL DEFAULT 1.0,
    max_multiplier          NUMERIC(4, 2) NOT NULL DEFAULT 2.0,
    recompute_interval_mins INT NOT NULL DEFAULT 10,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_dpc_thresholds CHECK (demand_threshold_low <= demand_threshold_high),
    CONSTRAINT chk_dpc_multipliers CHECK (min_multiplier <= max_multiplier)
);

CREATE TABLE zone_demand_snapshot (
    id                    BIGSERIAL PRIMARY KEY,
    zone_area_id          BIGINT NOT NULL REFERENCES zone_areas(id),
    service_type_id       BIGINT NOT NULL REFERENCES service_types(id),
    open_booking_requests INT NOT NULL DEFAULT 0,
    available_caregivers  INT NOT NULL DEFAULT 0,
    demand_ratio          NUMERIC(6, 3),
    computed_multiplier   NUMERIC(4, 2) NOT NULL DEFAULT 1.0,
    computed_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (zone_area_id, service_type_id)
);

CREATE TABLE zone_demand_snapshot_history (
    id                  BIGSERIAL PRIMARY KEY,
    zone_area_id        BIGINT NOT NULL,
    service_type_id     BIGINT NOT NULL,
    demand_ratio        NUMERIC(6, 3),
    computed_multiplier NUMERIC(4, 2),
    computed_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Not in the original design doc: resolveDayType() needs a holiday source (open design question
-- #3), and a static, ops-editable table is the safest default - no external API dependency, no
-- new library, and it degrades gracefully to weekday/weekend if a year is never populated.
CREATE TABLE holiday_calendar (
    holiday_date DATE PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    region       VARCHAR(100),
    is_active    BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_serviceability_pincode_zone ON serviceability_pincode (zone_area_id);
CREATE INDEX idx_zone_pricing_rules_lookup ON zone_pricing_rules (zone_service_pricing_id, day_type, is_active);
CREATE INDEX idx_serviceability_service_type_zone ON serviceability_service_type (zone_area_id, service_type_id, status);
CREATE INDEX idx_caregiver_zone_mapping_lookup ON caregiver_zone_mapping (zone_area_id, service_type_id, is_active);
CREATE INDEX idx_caregiver_zone_mapping_caregiver ON caregiver_zone_mapping (caregiver_id);
CREATE INDEX idx_zone_demand_snapshot_lookup ON zone_demand_snapshot (zone_area_id, service_type_id);
CREATE INDEX idx_zone_areas_geo ON zone_areas USING gist (ll_to_earth(centroid_lat, centroid_lng));
CREATE INDEX idx_waitlist_zone_service ON serviceability_waitlist (zone_area_id, service_type_id);

INSERT INTO service_types (code, name, pricing_unit)
VALUES ('childcare', 'Childcare', 'hourly'),
       ('senior_care', 'Senior Care', 'hourly'),
       ('adult_care', 'Adult Care', 'hourly'),
       ('pet_care', 'Pet Care', 'per_visit'),
       ('tutoring', 'Tutoring', 'per_session');

INSERT INTO holiday_calendar (holiday_date, name, region)
VALUES ('2026-01-26', 'Republic Day', NULL),
       ('2026-03-04', 'Holi', NULL),
       ('2026-08-15', 'Independence Day', NULL),
       ('2026-10-02', 'Gandhi Jayanti', NULL),
       ('2026-11-08', 'Diwali', NULL),
       ('2026-12-25', 'Christmas', NULL);
