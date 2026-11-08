--liquibase formatted sql

--changeset ektrepha:48
-- Audit trail for who viewed a nanny's verification document (nanny app spec: "every view saved
-- in document_access_log"). Written whenever a presigned view URL is actually handed out -
-- AdminNannyServiceImpl#documents/#uploadDocument (ops) and the new nanny self-service
-- GET .../documents/{id}/view-url (NannyVerificationServiceImpl#ownViewUrl).
CREATE TABLE document_access_log (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES nanny_verification(id),
    viewed_by_user_id BIGINT NOT NULL REFERENCES users(id),
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_document_access_log_document_id ON document_access_log(document_id);
