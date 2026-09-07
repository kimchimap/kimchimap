-- 공개 자격은 출판 플래그와 별개로 현재 유효한 국내산 항목의 존재를 요구한다.
CREATE FUNCTION app.has_domestic_origin(restaurant uuid, at_time timestamptz)
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
        WHERE o.restaurant_id=restaurant AND scope.scope_precision='SPECIFIC_ITEM'
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

-- 원산지 필드가 없는 전체 업소 수집의 과거 작업은 재개하지 않는다.
UPDATE app.ingestion_job SET status='CANCELLED',lease_owner=NULL,lease_until=NULL
WHERE source_id='00000000-0000-4000-8000-000000000100'
    AND status IN ('QUEUED','RUNNING','WAITING','PARTIAL','FAILED');
