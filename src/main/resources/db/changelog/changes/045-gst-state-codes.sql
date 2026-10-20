--liquibase formatted sql

--changeset ektrepha:45
-- Reference table for the CGST+SGST/UTGST vs IGST split (see GstSplitUtil) - sourced from
-- GST_State_Codes.xlsx (2026-10-19). code is the GSTIN's own 2-digit state code. uses_utgst is
-- true for a Union Territory with no legislature (Chandigarh, Dadra & Nagar Haveli and Daman &
-- Diu, Lakshadweep, Andaman & Nicobar Islands, Ladakh, Other Territory) - those charge UTGST
-- instead of SGST on an intra-state supply. Delhi, Puducherry and Jammu & Kashmir are UTs *with*
-- a legislature, so they charge SGST like a regular State. Codes 25 (old Daman & Diu, merged into
-- 26) and 28 (old Andhra Pradesh, now 37) are deliberately omitted - never use them for new records.
CREATE TABLE gst_state_codes (
    code VARCHAR(2) PRIMARY KEY,
    state_name VARCHAR(100) NOT NULL UNIQUE,
    is_union_territory BOOLEAN NOT NULL,
    uses_utgst BOOLEAN NOT NULL
);

INSERT INTO gst_state_codes (code, state_name, is_union_territory, uses_utgst) VALUES
    ('01', 'Jammu & Kashmir', true, false),
    ('02', 'Himachal Pradesh', false, false),
    ('03', 'Punjab', false, false),
    ('04', 'Chandigarh', true, true),
    ('05', 'Uttarakhand', false, false),
    ('06', 'Haryana', false, false),
    ('07', 'Delhi', true, false),
    ('08', 'Rajasthan', false, false),
    ('09', 'Uttar Pradesh', false, false),
    ('10', 'Bihar', false, false),
    ('11', 'Sikkim', false, false),
    ('12', 'Arunachal Pradesh', false, false),
    ('13', 'Nagaland', false, false),
    ('14', 'Manipur', false, false),
    ('15', 'Mizoram', false, false),
    ('16', 'Tripura', false, false),
    ('17', 'Meghalaya', false, false),
    ('18', 'Assam', false, false),
    ('19', 'West Bengal', false, false),
    ('20', 'Jharkhand', false, false),
    ('21', 'Odisha', false, false),
    ('22', 'Chhattisgarh', false, false),
    ('23', 'Madhya Pradesh', false, false),
    ('24', 'Gujarat', false, false),
    ('26', 'Dadra & Nagar Haveli and Daman & Diu', true, true),
    ('27', 'Maharashtra', false, false),
    ('29', 'Karnataka', false, false),
    ('30', 'Goa', false, false),
    ('31', 'Lakshadweep', true, true),
    ('32', 'Kerala', false, false),
    ('33', 'Tamil Nadu', false, false),
    ('34', 'Puducherry', true, false),
    ('35', 'Andaman & Nicobar Islands', true, true),
    ('36', 'Telangana', false, false),
    ('37', 'Andhra Pradesh', false, false),
    ('38', 'Ladakh', true, true),
    ('97', 'Other Territory', true, true);

-- utgst_paise alongside the existing cgst/sgst/igst split (migration 044) - a booking in a
-- business-state that's a legislature-less UT (see uses_utgst above) splits its GST into
-- CGST+UTGST, not CGST+SGST. Exactly one of (cgst+sgst), (cgst+utgst), or igst is ever non-zero.
ALTER TABLE booking ADD COLUMN utgst_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payment_transaction ADD COLUMN utgst_paise BIGINT NOT NULL DEFAULT 0;
