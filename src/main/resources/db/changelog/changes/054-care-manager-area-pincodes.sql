--liquibase formatted sql

--changeset ektrepha:54
-- Care Manager auto-assignment needs to know which of the two CM coverage areas
-- (BROOKEFIELD/WHITEFIELD) a parent is in. There's no existing data for this: the 104
-- fine-grained zone_areas seeded in migration 011 only happen to include one of the two
-- ("Whitefield", pincode 560066) under its own postal name, and "Brookefield" isn't an official
-- postal locality at all, so it was never seeded there. This is a deliberately small, separate
-- reference table rather than trying to retrofit zone_areas - confirmed with product:
-- BROOKEFIELD = 560037, WHITEFIELD = 560066 and 560048.
CREATE TABLE cm_area_pincodes (
    pincode  VARCHAR(6)  PRIMARY KEY,
    area     VARCHAR(20) NOT NULL CHECK (area IN ('BROOKEFIELD', 'WHITEFIELD'))
);

INSERT INTO cm_area_pincodes (pincode, area) VALUES
    ('560037', 'BROOKEFIELD'),
    ('560066', 'WHITEFIELD'),
    ('560048', 'WHITEFIELD');
