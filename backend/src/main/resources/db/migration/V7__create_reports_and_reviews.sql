INSERT INTO app.data_source(id,code,name,terms_reference,collection_allowed,republication_allowed)
VALUES ('dea158a9-07a9-4db9-8531-6096193af1ac','USER_REPORT','이용자 원산지 제보','제보별 공개 동의와 관리자 검수 기록',true,true);

CREATE TABLE app.report (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES app.member,
    restaurant_id uuid NOT NULL REFERENCES app.restaurant,
    current_revision_id uuid NOT NULL,
    state varchar(30) NOT NULL CHECK (state IN ('PENDING','APPROVED','REJECTED','NEEDS_MORE_INFO','WITHDRAWN')),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE app.report_revision (
    id uuid PRIMARY KEY,
    report_id uuid NOT NULL REFERENCES app.report,
    body jsonb NOT NULL,
    publication_consent boolean NOT NULL CHECK (publication_consent),
    consent_version varchar(30) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(id,report_id)
);
ALTER TABLE app.report ADD FOREIGN KEY (current_revision_id,id) REFERENCES app.report_revision(id,report_id) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE app.report_media (
    revision_id uuid NOT NULL REFERENCES app.report_revision,
    media_id uuid NOT NULL REFERENCES app.media,
    PRIMARY KEY(revision_id,media_id)
);
CREATE TABLE app.report_review (
    id uuid PRIMARY KEY,
    report_id uuid NOT NULL REFERENCES app.report,
    revision_id uuid NOT NULL,
    actor_id uuid NOT NULL REFERENCES app.member,
    decision varchar(30) NOT NULL CHECK (decision IN ('APPROVED','REJECTED','NEEDS_MORE_INFO','WITHDRAWN')),
    reason text NOT NULL CHECK (btrim(reason) <> ''),
    previous_version bigint NOT NULL,
    approved_scope_id uuid REFERENCES app.serving_scope,
    public_media_ids uuid[] NOT NULL DEFAULT '{}',
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (revision_id,report_id) REFERENCES app.report_revision(id,report_id)
);
CREATE TABLE app.report_request (
    owner_id uuid NOT NULL REFERENCES app.member,
    request_key uuid NOT NULL,
    request_hash varchar(64) NOT NULL,
    report_id uuid NOT NULL REFERENCES app.report,
    expires_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP + interval '24 hours',
    PRIMARY KEY(owner_id,request_key)
);
CREATE INDEX report_owner_page ON app.report(owner_id,created_at DESC,id);
CREATE INDEX report_review_queue ON app.report(state,created_at,id);
CREATE INDEX report_media_reference ON app.report_media(media_id);
CREATE FUNCTION app.reject_report_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '제보 제출본과 검토 이력은 변경할 수 없습니다';
END;
$$;
CREATE TRIGGER report_revision_immutable BEFORE UPDATE OR DELETE ON app.report_revision
    FOR EACH ROW EXECUTE FUNCTION app.reject_report_history_mutation();
CREATE TRIGGER report_review_immutable BEFORE UPDATE OR DELETE ON app.report_review
    FOR EACH ROW EXECUTE FUNCTION app.reject_report_history_mutation();
CREATE TRIGGER report_media_immutable BEFORE UPDATE OR DELETE ON app.report_media
    FOR EACH ROW EXECUTE FUNCTION app.reject_report_history_mutation();
