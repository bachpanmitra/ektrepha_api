--liquibase formatted sql

--changeset ektrepha:44
-- Neither booking nor payment_transaction has ever persisted the subtotal/platform-fee/GST split
-- that priced it - only the net total_amount (booking) and amount (payment_transaction) survive.
-- That's a real gap for GST/accounting records, and it bites hardest on a FIRST5_FEE_WAIVER trial
-- booking: its total_amount is 0, so without this breakdown there's no permanent record of what
-- subtotal/fee/GST were actually waived.
--
-- booking gets the breakdown at creation time (the only point every pricing input - zone, date,
-- mode - is known); payment_transaction gets its own copy at initiate time (summed across a
-- monthly series' occurrences, same as its existing amount column) so each payment attempt is a
-- self-contained GST/invoice record even if pricing rules move before a later attempt.
--
-- platform_fee_gst_paise is the total GST on the platform fee (unchanged - every existing caller
-- that reads it keeps working); cgst/sgst/igst split that same total by comparing the booking's
-- care-address state against app.pricing.business-state (see HourlyCareServiceImpl#splitGst) -
-- CGST+SGST (split evenly) for an intra-state booking, IGST (the whole amount) for inter-state.
-- Exactly one of (cgst+sgst) or igst is ever non-zero for a given row.
ALTER TABLE booking ADD COLUMN subtotal_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE booking ADD COLUMN platform_fee_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE booking ADD COLUMN platform_fee_gst_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE booking ADD COLUMN cgst_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE booking ADD COLUMN sgst_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE booking ADD COLUMN igst_paise BIGINT NOT NULL DEFAULT 0;

ALTER TABLE payment_transaction ADD COLUMN subtotal_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payment_transaction ADD COLUMN platform_fee_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payment_transaction ADD COLUMN platform_fee_gst_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payment_transaction ADD COLUMN cgst_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payment_transaction ADD COLUMN sgst_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payment_transaction ADD COLUMN igst_paise BIGINT NOT NULL DEFAULT 0;
