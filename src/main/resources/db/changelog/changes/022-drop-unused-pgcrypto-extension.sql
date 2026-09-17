--liquibase formatted sql

--changeset ektrepha:22
-- pgcrypto was added in migration 004 to back gen_random_uuid() on the UUID-keyed identity tables.
-- Migration 005 reverted those tables to BIGSERIAL and dropped/recreated them without pgcrypto's
-- functions. Confirmed via grep that nothing in the codebase calls gen_random_uuid()/crypt()/
-- digest()/pgp_sym_encrypt() today, so the extension is dead weight.

DROP EXTENSION IF EXISTS pgcrypto;
