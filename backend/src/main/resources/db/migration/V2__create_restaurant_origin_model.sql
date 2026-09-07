CREATE TABLE app.data_source (
    id uuid PRIMARY KEY,
    code varchar(80) NOT NULL UNIQUE,
    name varchar(200) NOT NULL CHECK (btrim(name) <> ''),
    official_url text,
    terms_reference text,
    collection_allowed boolean NOT NULL DEFAULT false,
    republication_allowed boolean NOT NULL DEFAULT false
);

CREATE TABLE app.restaurant (
    id uuid PRIMARY KEY,
    name varchar(200) NOT NULL CHECK (btrim(name) <> ''),
    address text NOT NULL,
    original_address text,
    business_status varchar(20) NOT NULL CHECK (business_status IN ('OPEN', 'CLOSED', 'UNKNOWN')),
    location public.geometry(Point, 4326),
    coordinate_status varchar(20) NOT NULL CHECK (coordinate_status IN ('VERIFIED', 'MISSING', 'INVALID', 'REVIEW_REQUIRED')),
    published boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((coordinate_status = 'VERIFIED') = (location IS NOT NULL)),
    CHECK (location IS NULL OR (NOT public.ST_IsEmpty(location) AND public.ST_X(location) BETWEEN -180 AND 180 AND public.ST_Y(location) BETWEEN -90 AND 90))
);
CREATE INDEX restaurant_location_gist ON app.restaurant USING gist(location);
CREATE INDEX restaurant_geography_gist ON app.restaurant USING gist((location::public.geography));

CREATE TABLE app.restaurant_external_id (
    source_id uuid NOT NULL REFERENCES app.data_source,
    external_id varchar(200) NOT NULL,
    restaurant_id uuid NOT NULL REFERENCES app.restaurant,
    original_status text,
    PRIMARY KEY (source_id, external_id)
);
CREATE INDEX restaurant_external_restaurant ON app.restaurant_external_id(restaurant_id);

CREATE TABLE app.serving_scope (
    id uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL REFERENCES app.restaurant,
    name varchar(200) NOT NULL CHECK (btrim(name) <> ''),
    original_name text,
    usage varchar(30) NOT NULL CHECK (usage IN ('SIDE_DISH', 'STEW', 'MAIN_DISH', 'OTHER', 'UNKNOWN')),
    scope_precision varchar(30) NOT NULL CHECK (scope_precision IN ('SPECIFIC_ITEM', 'UNSPECIFIED')),
    UNIQUE (id, restaurant_id)
);
CREATE INDEX serving_scope_restaurant ON app.serving_scope(restaurant_id);

CREATE TABLE app.ingredient (
    id uuid PRIMARY KEY,
    code varchar(80) NOT NULL UNIQUE,
    name varchar(100) NOT NULL CHECK (btrim(name) <> ''),
    category varchar(80) NOT NULL,
    active boolean NOT NULL DEFAULT true
);
CREATE TABLE app.country (
    code varchar(2) PRIMARY KEY CHECK (code ~ '^[A-Z]{2}$'),
    name varchar(100) NOT NULL,
    active boolean NOT NULL DEFAULT true
);
CREATE TABLE app.labeling_rule (
    id uuid PRIMARY KEY,
    ingredient_id uuid NOT NULL REFERENCES app.ingredient,
    source_id uuid NOT NULL REFERENCES app.data_source,
    applicability text NOT NULL,
    rule_kind varchar(20) NOT NULL CHECK (rule_kind IN ('LEGAL', 'VOLUNTARY')),
    effective_from date,
    effective_until date,
    CHECK (effective_until IS NULL OR effective_from IS NULL OR effective_from < effective_until)
);

CREATE TABLE app.evidence (
    id uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL REFERENCES app.restaurant,
    source_id uuid NOT NULL REFERENCES app.data_source,
    source_identifier text NOT NULL,
    kind varchar(40) NOT NULL CHECK (kind IN ('SIGNBOARD_OBSERVATION', 'USER_SUBMISSION', 'SUPPLY_VERIFICATION')),
    public_reference text,
    public_summary text,
    publicly_visible boolean NOT NULL DEFAULT false,
    collected_at timestamptz NOT NULL,
    last_fetch_succeeded_at timestamptz NOT NULL,
    UNIQUE (id, restaurant_id),
    CHECK (last_fetch_succeeded_at >= collected_at)
);

CREATE TABLE app.origin_record (
    id uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL,
    scope_id uuid NOT NULL,
    ingredient_id uuid NOT NULL REFERENCES app.ingredient,
    evidence_id uuid NOT NULL,
    classification varchar(30) NOT NULL CHECK (classification IN ('DOMESTIC', 'IMPORTED_SPECIFIED', 'IMPORTED_UNSPECIFIED', 'MIXED', 'UNKNOWN')),
    original_expression text NOT NULL,
    observed_at timestamptz,
    observed_precision varchar(10) NOT NULL CHECK (observed_precision IN ('UNKNOWN', 'DATE', 'INSTANT')),
    source_updated_at timestamptz,
    source_updated_precision varchar(10) NOT NULL CHECK (source_updated_precision IN ('UNKNOWN', 'DATE', 'INSTANT')),
    reviewed_at timestamptz,
    review_status varchar(20) NOT NULL CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    valid_from timestamptz,
    valid_until timestamptz,
    revision bigint NOT NULL CHECK (revision > 0),
    created_transaction xid8 NOT NULL DEFAULT pg_current_xact_id(),
    supersedes_id uuid,
    FOREIGN KEY (scope_id, restaurant_id) REFERENCES app.serving_scope(id, restaurant_id),
    FOREIGN KEY (evidence_id, restaurant_id) REFERENCES app.evidence(id, restaurant_id),
    UNIQUE (id, scope_id, ingredient_id),
    FOREIGN KEY (supersedes_id, scope_id, ingredient_id) REFERENCES app.origin_record(id, scope_id, ingredient_id),
    CHECK (id IS DISTINCT FROM supersedes_id),
    CHECK ((observed_at IS NULL) = (observed_precision = 'UNKNOWN')),
    CHECK ((source_updated_at IS NULL) = (source_updated_precision = 'UNKNOWN')),
    CHECK (review_status <> 'APPROVED' OR reviewed_at IS NOT NULL),
    CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_from < valid_until)
);
CREATE INDEX origin_record_scope_ingredient ON app.origin_record(scope_id, ingredient_id);

CREATE TABLE app.origin_component (
    record_id uuid NOT NULL REFERENCES app.origin_record,
    component_index smallint NOT NULL CHECK (component_index >= 0),
    country_code varchar(2) REFERENCES app.country,
    origin_kind varchar(30) NOT NULL CHECK (origin_kind IN ('DOMESTIC', 'IMPORTED_SPECIFIED', 'IMPORTED_UNSPECIFIED')),
    ratio numeric(7,6) CHECK (ratio > 0 AND ratio <= 1),
    PRIMARY KEY (record_id, component_index),
    UNIQUE NULLS NOT DISTINCT (record_id, country_code),
    CHECK ((origin_kind = 'DOMESTIC' AND country_code IS NOT NULL AND country_code = 'KR')
        OR (origin_kind = 'IMPORTED_SPECIFIED' AND country_code IS NOT NULL AND country_code <> 'KR')
        OR (origin_kind = 'IMPORTED_UNSPECIFIED' AND country_code IS NULL))
);

-- 행 단위 CHECK로 처리할 수 없는 구성 전체의 정합성을 커밋 시 확인한다.
CREATE FUNCTION app.validate_origin_composition() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    target uuid;
    classification text;
    component_count bigint;
    matching_count bigint;
    ratio_count bigint;
    ratio_sum numeric;
BEGIN
    IF TG_TABLE_NAME = 'origin_record' THEN target := NEW.id;
    ELSIF TG_OP = 'DELETE' THEN target := OLD.record_id;
    ELSE target := NEW.record_id;
    END IF;
    SELECT r.classification INTO classification FROM app.origin_record r WHERE id = target;
    IF NOT FOUND THEN RETURN NULL; END IF;
    SELECT count(*), count(*) FILTER (WHERE origin_kind = classification),
           count(ratio), coalesce(sum(ratio), 0)
    INTO component_count, matching_count, ratio_count, ratio_sum
    FROM app.origin_component WHERE record_id = target;
    IF (classification = 'UNKNOWN' AND component_count <> 0)
       OR (classification = 'MIXED' AND component_count < 2)
       OR (classification NOT IN ('UNKNOWN', 'MIXED') AND (component_count <> 1 OR matching_count <> 1))
       OR ratio_sum > 1
       OR (ratio_count = component_count AND component_count > 0 AND ratio_sum <> 1)
       OR (ratio_count < component_count AND ratio_sum >= 1) THEN
        RAISE EXCEPTION 'Invalid origin composition' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER origin_record_composition AFTER INSERT ON app.origin_record
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION app.validate_origin_composition();
CREATE CONSTRAINT TRIGGER origin_component_composition AFTER INSERT OR UPDATE OR DELETE ON app.origin_component
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION app.validate_origin_composition();

CREATE FUNCTION app.reject_origin_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Origin revisions are immutable' USING ERRCODE = '23514';
END $$;
CREATE TRIGGER origin_record_immutable BEFORE UPDATE OR DELETE ON app.origin_record
    FOR EACH ROW EXECUTE FUNCTION app.reject_origin_mutation();
CREATE TRIGGER origin_component_immutable BEFORE UPDATE OR DELETE ON app.origin_component
    FOR EACH ROW EXECUTE FUNCTION app.reject_origin_mutation();

CREATE FUNCTION app.require_component_creation_transaction() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM app.origin_record WHERE id = NEW.record_id
        AND created_transaction = pg_current_xact_id()) THEN
        RAISE EXCEPTION 'Components must be created with their revision' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER origin_component_same_transaction BEFORE INSERT ON app.origin_component
    FOR EACH ROW EXECUTE FUNCTION app.require_component_creation_transaction();

CREATE TABLE app.origin_withdrawal (
    record_id uuid PRIMARY KEY REFERENCES app.origin_record,
    reason text NOT NULL CHECK (btrim(reason) <> ''),
    actor_reference varchar(200) NOT NULL,
    withdrawn_at timestamptz NOT NULL
);
CREATE TABLE app.origin_publication (
    scope_id uuid NOT NULL REFERENCES app.serving_scope,
    ingredient_id uuid NOT NULL REFERENCES app.ingredient,
    selected_record_id uuid,
    status varchar(20) NOT NULL CHECK (status IN ('CURRENT', 'DISPUTED', 'UNAVAILABLE')),
    policy_version integer NOT NULL,
    reason text NOT NULL,
    decided_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (scope_id, ingredient_id),
    FOREIGN KEY (selected_record_id, scope_id, ingredient_id) REFERENCES app.origin_record(id, scope_id, ingredient_id),
    CHECK ((status = 'CURRENT') = (selected_record_id IS NOT NULL))
);
CREATE TABLE app.origin_publication_history (
    id uuid PRIMARY KEY,
    scope_id uuid NOT NULL,
    ingredient_id uuid NOT NULL,
    selected_record_id uuid,
    status varchar(20) NOT NULL CHECK (status IN ('CURRENT', 'DISPUTED', 'UNAVAILABLE')),
    reason text NOT NULL,
    actor_reference varchar(200) NOT NULL,
    decided_at timestamptz NOT NULL,
    policy_version integer NOT NULL,
    FOREIGN KEY (scope_id, ingredient_id) REFERENCES app.origin_publication,
    FOREIGN KEY (selected_record_id, scope_id, ingredient_id) REFERENCES app.origin_record(id, scope_id, ingredient_id)
);

CREATE TRIGGER origin_withdrawal_immutable BEFORE UPDATE OR DELETE ON app.origin_withdrawal
    FOR EACH ROW EXECUTE FUNCTION app.reject_origin_mutation();
CREATE TRIGGER origin_publication_history_immutable BEFORE UPDATE OR DELETE ON app.origin_publication_history
    FOR EACH ROW EXECUTE FUNCTION app.reject_origin_mutation();

CREATE TABLE app.designation (
    id uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL REFERENCES app.restaurant,
    source_id uuid NOT NULL REFERENCES app.data_source,
    external_id text NOT NULL,
    scheme_name text NOT NULL,
    applicable_items text NOT NULL,
    criteria_original text NOT NULL,
    designated_on date,
    expires_on date,
    cancelled_on date,
    publicly_visible boolean NOT NULL DEFAULT false,
    UNIQUE (source_id, external_id)
);

-- 참조 카탈로그만 초기화한다. 실제 업소·원산지·법정 대상은 생성하지 않는다.
INSERT INTO app.country(code, name) VALUES ('KR', '대한민국'), ('CN', '중국'), ('US', '미국'), ('AU', '호주'), ('VN', '베트남');
INSERT INTO app.ingredient(id, code, name, category) VALUES
('00000000-0000-4000-8000-000000000001', 'napa-cabbage', '배추', 'vegetable'),
('00000000-0000-4000-8000-000000000002', 'red-pepper-powder', '고춧가루', 'seasoning'),
('00000000-0000-4000-8000-000000000003', 'rice', '쌀', 'grain'),
('00000000-0000-4000-8000-000000000004', 'beef', '쇠고기', 'meat'),
('00000000-0000-4000-8000-000000000005', 'pork', '돼지고기', 'meat'),
('00000000-0000-4000-8000-000000000006', 'chicken', '닭고기', 'meat');
