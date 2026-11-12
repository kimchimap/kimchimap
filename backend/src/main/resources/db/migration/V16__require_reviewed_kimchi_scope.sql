-- 기존 품목은 이름으로 추정하지 않고 재검토 전까지 미확인으로 보존한다.
ALTER TABLE app.serving_scope ADD COLUMN food_kind varchar(20) NOT NULL DEFAULT 'UNKNOWN'
    CHECK (food_kind IN ('UNKNOWN','KIMCHI'));

CREATE OR REPLACE FUNCTION app.has_domestic_origin(restaurant uuid, at_time timestamptz)
RETURNS boolean LANGUAGE sql STABLE AS $$
    WITH active AS (
        SELECT o.scope_id,o.ingredient_id,o.classification,
            (e.publicly_visible AND s.republication_allowed) publicly_visible,
            jsonb_build_object('classification',o.classification,'components',
                (SELECT coalesce(jsonb_agg(jsonb_build_array(c.origin_kind,c.country_code,c.ratio)
                    ORDER BY c.country_code NULLS LAST),'[]'::jsonb)
                 FROM app.origin_component c WHERE c.record_id=o.id)) signature
        FROM app.origin_record o
        JOIN app.serving_scope scope ON scope.id=o.scope_id
        JOIN app.evidence e ON e.id=o.evidence_id
        JOIN app.data_source s ON s.id=e.source_id
        WHERE o.restaurant_id=restaurant AND scope.scope_precision='SPECIFIC_ITEM' AND scope.food_kind='KIMCHI'
            AND o.ingredient_id IN (SELECT id FROM app.ingredient WHERE code IN ('napa-cabbage','red-pepper-powder'))
            AND o.review_status='APPROVED' AND o.reviewed_at<=at_time
            AND (o.valid_from IS NULL OR o.valid_from<=at_time)
            AND (o.valid_until IS NULL OR o.valid_until>at_time)
            AND NOT EXISTS(SELECT 1 FROM app.origin_withdrawal w WHERE w.record_id=o.id AND w.withdrawn_at<=at_time)
    ), consistent AS (
        SELECT scope_id,ingredient_id FROM active GROUP BY scope_id,ingredient_id
        HAVING count(DISTINCT signature)=1
    )
    SELECT EXISTS(SELECT 1 FROM active a JOIN consistent c USING(scope_id,ingredient_id)
                  WHERE a.classification='DOMESTIC' AND a.publicly_visible)
$$;
