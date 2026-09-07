CREATE TABLE app.origin_correction (
    id uuid PRIMARY KEY,
    scope_id uuid NOT NULL REFERENCES app.serving_scope,
    ingredient_id uuid NOT NULL REFERENCES app.ingredient,
    actor_id uuid NOT NULL REFERENCES app.member,
    reason varchar(2000) NOT NULL CHECK (btrim(reason) <> ''),
    previous_version bigint NOT NULL,
    withdrawn_record_ids uuid[] NOT NULL CHECK (cardinality(withdrawn_record_ids) BETWEEN 1 AND 20),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX origin_correction_history ON app.origin_correction(scope_id,ingredient_id,created_at);
CREATE TRIGGER origin_correction_immutable BEFORE UPDATE OR DELETE ON app.origin_correction
    FOR EACH ROW EXECUTE FUNCTION app.reject_origin_mutation();
