CREATE TABLE app.ingestion_request (
    actor_id uuid NOT NULL REFERENCES app.member,
    request_key uuid NOT NULL,
    request_hash varchar(64) NOT NULL,
    job_id uuid NOT NULL REFERENCES app.ingestion_job,
    expires_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP + interval '24 hours',
    PRIMARY KEY (actor_id, request_key)
);
CREATE INDEX ingestion_request_expiry ON app.ingestion_request(expires_at);
CREATE INDEX report_request_expiry ON app.report_request(expires_at);
CREATE TABLE app.ingestion_request_audit (
    id uuid PRIMARY KEY,
    actor_id uuid NOT NULL REFERENCES app.member,
    job_id uuid NOT NULL REFERENCES app.ingestion_job,
    source_id uuid NOT NULL REFERENCES app.data_source,
    reason varchar(2000) NOT NULL CHECK (btrim(reason) <> ''),
    page_budget integer NOT NULL CHECK (page_budget BETWEEN 1 AND 100),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ingestion_request_audit_actor_time ON app.ingestion_request_audit(actor_id,created_at);
CREATE TRIGGER ingestion_request_audit_immutable BEFORE UPDATE OR DELETE ON app.ingestion_request_audit
    FOR EACH ROW EXECUTE FUNCTION app.reject_ingestion_event_mutation();
