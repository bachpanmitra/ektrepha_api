--liquibase formatted sql

--changeset ektrepha:34
-- Post-login automatic care-location selection (see com.ektrepha.parent.controller.CareLocationController
-- and com.ektrepha.location): lets a returning parent's last-confirmed care address be restored
-- without asking again, distinct from parent_address.is_primary (the parent's own "default"
-- address for other purposes). NULL until the parent ever confirms a care location. ON DELETE is
-- deliberately left unset (default RESTRICT-like NO ACTION) - ParentAddressServiceImpl.delete
-- clears this column itself before removing the address, the same way it already reassigns
-- is_primary, so this FK is never actually put under delete pressure in practice.

ALTER TABLE parent ADD COLUMN last_selected_address_id BIGINT REFERENCES parent_address(id);
