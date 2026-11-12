INSERT INTO app.data_source(id,code,name,terms_reference,republication_allowed)
VALUES ('e9c56d7a-d847-4ef4-a925-c3c95da2a3e3','ADMIN_CORRECTION','관리자 확인 정정','관리자가 근거와 업소 연락처 공개 범위를 확인한 정정',true);
CREATE TABLE app.restaurant_correction (
    id uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL REFERENCES app.restaurant,
    actor_id uuid NOT NULL REFERENCES app.member,
    previous_version bigint NOT NULL,
    before_snapshot jsonb NOT NULL,
    after_snapshot jsonb NOT NULL,
    reason varchar(2000) NOT NULL CHECK(btrim(reason) <> ''),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX restaurant_correction_history ON app.restaurant_correction(restaurant_id,created_at DESC,id);
CREATE TRIGGER restaurant_correction_immutable BEFORE UPDATE OR DELETE ON app.restaurant_correction
    FOR EACH ROW EXECUTE FUNCTION app.reject_origin_mutation();
