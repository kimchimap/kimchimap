ALTER TABLE app.restaurant ADD COLUMN phone_number varchar(30);
ALTER TABLE app.restaurant ADD COLUMN phone_display varchar(64);
ALTER TABLE app.restaurant ADD COLUMN phone_source_id uuid REFERENCES app.data_source;
ALTER TABLE app.restaurant ADD CONSTRAINT restaurant_phone_format CHECK (
    phone_number IS NULL OR phone_number ~ '^\+?[0-9]{8,15}$');
ALTER TABLE app.restaurant ADD CONSTRAINT restaurant_phone_provenance CHECK (
    (phone_number IS NULL AND phone_display IS NULL AND phone_source_id IS NULL)
    OR (phone_number IS NOT NULL AND phone_display IS NOT NULL AND phone_source_id IS NOT NULL));

CREATE TABLE app.ingestion_source (
    source_id uuid PRIMARY KEY REFERENCES app.data_source,
    adapter varchar(80) NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    interval_seconds integer NOT NULL DEFAULT 86400 CHECK (interval_seconds >= 86400),
    page_size integer NOT NULL DEFAULT 100 CHECK (page_size BETWEEN 1 AND 100),
    next_run_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_completed_until timestamptz,
    last_full_completed_at timestamptz
);
CREATE TABLE app.ingestion_job (
    id uuid PRIMARY KEY,
    source_id uuid NOT NULL REFERENCES app.ingestion_source,
    mode varchar(20) NOT NULL CHECK (mode IN ('INCREMENTAL', 'FULL')),
    status varchar(20) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'WAITING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED')),
    since_at timestamptz,
    until_at timestamptz NOT NULL,
    page_size integer NOT NULL CHECK (page_size BETWEEN 1 AND 100),
    next_page integer NOT NULL DEFAULT 1 CHECK (next_page > 0),
    max_pages integer NOT NULL CHECK (max_pages BETWEEN 1 AND 100000000),
    pages_processed integer NOT NULL DEFAULT 0,
    total_count bigint,
    read_count bigint NOT NULL DEFAULT 0,
    changed_count bigint NOT NULL DEFAULT 0,
    quarantined_count bigint NOT NULL DEFAULT 0,
    full_listing_completed boolean NOT NULL DEFAULT false,
    lease_owner uuid,
    fencing_token bigint NOT NULL DEFAULT 0,
    lease_until timestamptz,
    next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retry_count integer NOT NULL DEFAULT 0,
    error_code varchar(80),
    requested_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (since_at IS NULL OR since_at < until_at)
);
CREATE UNIQUE INDEX ingestion_one_active_source ON app.ingestion_job(source_id)
    WHERE status IN ('QUEUED', 'RUNNING', 'WAITING');
CREATE INDEX ingestion_due_job ON app.ingestion_job(next_attempt_at, requested_at)
    WHERE status IN ('QUEUED', 'RUNNING', 'WAITING');

CREATE TABLE app.ingestion_job_event (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES app.ingestion_job,
    page_no integer NOT NULL,
    fencing_token bigint NOT NULL,
    status varchar(20) NOT NULL,
    error_code varchar(80),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX ingestion_job_event_history ON app.ingestion_job_event(job_id, id);
CREATE FUNCTION app.reject_ingestion_event_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '수집 오류·처리 이력은 변경할 수 없습니다';
END;
$$;
CREATE TRIGGER ingestion_job_event_immutable BEFORE UPDATE OR DELETE ON app.ingestion_job_event
    FOR EACH ROW EXECUTE FUNCTION app.reject_ingestion_event_mutation();

CREATE TABLE app.restaurant_source_record (
    source_id uuid NOT NULL,
    external_id varchar(200) NOT NULL,
    content_hash varchar(64) NOT NULL,
    source_updated_at timestamptz,
    source_modified_at timestamptz,
    original_x text,
    original_y text,
    original_srid integer NOT NULL,
    original_phone varchar(64),
    first_collected_at timestamptz NOT NULL,
    last_fetch_succeeded_at timestamptz NOT NULL,
    last_seen_job_id uuid NOT NULL REFERENCES app.ingestion_job,
    missing_since_job_id uuid REFERENCES app.ingestion_job,
    PRIMARY KEY (source_id, external_id),
    FOREIGN KEY (source_id, external_id) REFERENCES app.restaurant_external_id
);
CREATE TABLE app.restaurant_manual_override (
    restaurant_id uuid NOT NULL REFERENCES app.restaurant,
    field_name varchar(40) NOT NULL CHECK (field_name IN ('name', 'address', 'location', 'business_status', 'phone')),
    reason text NOT NULL CHECK (btrim(reason) <> ''),
    actor_reference varchar(200) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active boolean NOT NULL DEFAULT true,
    PRIMARY KEY (restaurant_id, field_name)
);
CREATE TABLE app.ingestion_quarantine (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES app.ingestion_job,
    page_no integer NOT NULL,
    row_index integer NOT NULL,
    external_id varchar(200),
    error_code varchar(80) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(job_id, page_no, row_index)
);
CREATE TABLE app.ingestion_issue (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES app.ingestion_job,
    restaurant_id uuid REFERENCES app.restaurant,
    code varchar(80) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE app.restaurant_match_candidate (
    source_id uuid NOT NULL REFERENCES app.data_source,
    external_id varchar(200) NOT NULL,
    candidate_restaurant_id uuid NOT NULL REFERENCES app.restaurant,
    job_id uuid NOT NULL REFERENCES app.ingestion_job,
    status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING', 'MATCHED', 'REJECTED')),
    PRIMARY KEY(source_id, external_id, candidate_restaurant_id)
);

INSERT INTO app.data_source(id, code, name, official_url, terms_reference, collection_allowed, republication_allowed)
VALUES ('00000000-0000-4000-8000-000000000100', 'mois-general-restaurants', '행정안전부 일반음식점 인허가 정보',
    'https://www.data.go.kr/data/15154916/openapi.do', '2026-09-07 공식 페이지: 이용허락범위 제한 없음, 무료', true, true);
INSERT INTO app.ingestion_source(source_id, adapter)
VALUES ('00000000-0000-4000-8000-000000000100', 'MOIS_GENERAL_RESTAURANTS');
