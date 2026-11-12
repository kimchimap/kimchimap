CREATE TABLE app.information_report (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES app.member,
    restaurant_id uuid NOT NULL REFERENCES app.restaurant,
    request_key uuid NOT NULL,
    request_hash varchar(64) NOT NULL,
    category varchar(30) NOT NULL CHECK (category IN ('ORIGIN_CHANGED','CLOSED','LOCATION','CONTACT','OTHER')),
    description varchar(2000) NOT NULL CHECK (btrim(description) <> ''),
    state varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','RESOLVED','DISMISSED')),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(owner_id,request_key)
);
CREATE INDEX information_report_owner_page ON app.information_report(owner_id,created_at DESC,id);
CREATE INDEX information_report_queue ON app.information_report(state,created_at DESC,id);
CREATE TABLE app.information_report_media (
    report_id uuid NOT NULL REFERENCES app.information_report,
    media_id uuid NOT NULL REFERENCES app.media,
    PRIMARY KEY(report_id,media_id)
);
CREATE TABLE app.information_report_review (
    report_id uuid PRIMARY KEY REFERENCES app.information_report,
    actor_id uuid NOT NULL REFERENCES app.member,
    decision varchar(20) NOT NULL CHECK (decision IN ('RESOLVED','DISMISSED')),
    reason varchar(2000) NOT NULL CHECK (btrim(reason) <> ''),
    previous_version bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER information_report_submission_immutable
    BEFORE UPDATE OF owner_id,restaurant_id,request_key,request_hash,category,description,created_at ON app.information_report
    FOR EACH ROW EXECUTE FUNCTION app.reject_report_history_mutation();
CREATE TRIGGER information_report_review_immutable BEFORE UPDATE OR DELETE ON app.information_report_review
    FOR EACH ROW EXECUTE FUNCTION app.reject_report_history_mutation();
CREATE TRIGGER information_report_media_immutable BEFORE UPDATE OR DELETE ON app.information_report_media
    FOR EACH ROW EXECUTE FUNCTION app.reject_report_history_mutation();
