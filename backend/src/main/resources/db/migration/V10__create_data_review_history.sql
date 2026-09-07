ALTER TABLE app.data_source ADD COLUMN designation_allowed boolean NOT NULL DEFAULT false;
ALTER TABLE app.designation ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE app.designation ADD COLUMN reviewed_at timestamptz;
CREATE TABLE app.designation_revision (
    id uuid PRIMARY KEY,
    designation_id uuid NOT NULL REFERENCES app.designation,
    version bigint NOT NULL,
    snapshot jsonb NOT NULL,
    actor_id uuid NOT NULL REFERENCES app.member,
    reason varchar(2000) NOT NULL CHECK (btrim(reason) <> ''),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(designation_id,version)
);
CREATE TRIGGER designation_revision_immutable BEFORE UPDATE OR DELETE ON app.designation_revision
    FOR EACH ROW EXECUTE FUNCTION app.reject_origin_mutation();
CREATE TABLE app.restaurant_match_review (
    id uuid NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    source_id uuid NOT NULL REFERENCES app.data_source,
    external_id varchar(200) NOT NULL,
    observation jsonb NOT NULL,
    observed_at timestamptz NOT NULL,
    job_id uuid NOT NULL REFERENCES app.ingestion_job,
    version bigint NOT NULL DEFAULT 0,
    state varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','MATCHED','DISTINCT')),
    restaurant_id uuid REFERENCES app.restaurant,
    PRIMARY KEY(source_id,external_id)
);
CREATE TABLE app.restaurant_match_audit (
    id uuid PRIMARY KEY,
    source_id uuid NOT NULL REFERENCES app.data_source,
    external_id varchar(200) NOT NULL,
    actor_id uuid NOT NULL REFERENCES app.member,
    decision varchar(20) NOT NULL CHECK(decision IN ('MATCHED','DISTINCT')),
    restaurant_id uuid NOT NULL REFERENCES app.restaurant,
    observation jsonb NOT NULL,
    previous_version bigint NOT NULL,
    reason varchar(2000) NOT NULL CHECK(btrim(reason) <> ''),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER restaurant_match_audit_immutable BEFORE UPDATE OR DELETE ON app.restaurant_match_audit
    FOR EACH ROW EXECUTE FUNCTION app.reject_origin_mutation();
