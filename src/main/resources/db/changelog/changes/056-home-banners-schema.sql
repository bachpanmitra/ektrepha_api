--liquibase formatted sql

--changeset ektrepha:56
-- PRD: Admin Home Page Banner Management (Phase 1 / MVP) — admin-managed promotional banners
-- shown in a carousel on the home page. sort_order decides on-screen order; status (Draft/
-- Scheduled/Live/Expired/Inactive) is never stored — it's computed from is_active/start_at/end_at
-- at read time, same reasoning as booking status elsewhere in this codebase being derived, not
-- duplicated. care_type reuses this app's CHILDCARE/SENIOR_CARE vocabulary (home_banners.care_type
-- is a single value here — Phase 1 ships before the PRD's "Child care / Elder care switch" exists
-- on the home page, so one banner never needs to target more than one care type at a time yet).
CREATE TABLE home_banners (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	title VARCHAR(60) NOT NULL,
	cta_text VARCHAR(30),
	image_mobile_url TEXT NOT NULL,
	image_desktop_url TEXT,
	link_type VARCHAR(20) NOT NULL,
	link_target TEXT,
	care_type VARCHAR(20) NOT NULL,
	start_at TIMESTAMPTZ NOT NULL,
	end_at TIMESTAMPTZ,
	sort_order INT NOT NULL DEFAULT 0,
	is_active BOOLEAN NOT NULL DEFAULT FALSE,
	-- Null until the first time is_active is set true — distinguishes DRAFT (never published)
	-- from INACTIVE (was live/scheduled, then turned off) when is_active is false. See
	-- HomeBanner.computeStatus.
	published_at TIMESTAMPTZ,
	created_by BIGINT NOT NULL REFERENCES users (id),
	updated_by BIGINT NOT NULL REFERENCES users (id),
	created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	deleted_at TIMESTAMPTZ,
	CONSTRAINT chk_home_banners_link_type CHECK (link_type IN ('NONE', 'SCREEN', 'INTERNAL_URL', 'EXTERNAL_URL')),
	CONSTRAINT chk_home_banners_end_after_start CHECK (end_at IS NULL OR end_at > start_at)
);

-- The public "live banners" read is the hot path (every home page load) and always filters on
-- these three; partial on deleted_at IS NULL keeps the index small as soft-deleted rows accumulate.
CREATE INDEX idx_home_banners_live ON home_banners (care_type, is_active, start_at, end_at) WHERE deleted_at IS NULL;
