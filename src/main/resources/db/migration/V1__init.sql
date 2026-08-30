-- Baseline migration. Establishes Flyway as the single source of truth
-- for schema going forward -- no hand-edited schema changes after this.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
