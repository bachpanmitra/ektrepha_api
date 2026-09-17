--liquibase formatted sql

--changeset ektrepha:28
-- P3 address deletion must check whether a non-terminal booking references the address before
-- allowing the delete (booking.address_id is nullable, so an unguarded delete would silently
-- blank a confirmed booking's location). That existsBy lookup filters on address_id with no
-- index backing it today.

CREATE INDEX idx_booking_address_id ON booking(address_id);
