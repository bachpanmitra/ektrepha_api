--liquibase formatted sql

--changeset bachpanmitra:7
-- Seed data for the language/skill catalogs referenced by nanny profile
-- management and search filters. Idempotent via ON CONFLICT DO NOTHING so
-- re-running (or applying to an environment that already has these rows
-- from manual seeding) is safe.

INSERT INTO language (name, is_active) VALUES
    ('Hindi', true),
    ('English', true),
    ('Kannada', true),
    ('Tamil', true)
ON CONFLICT (name) DO NOTHING;

INSERT INTO skill (name) VALUES
    ('Infant care'),
    ('Special needs'),
    ('First aid'),
    ('Cooking'),
    ('Homework help')
ON CONFLICT (name) DO NOTHING;
