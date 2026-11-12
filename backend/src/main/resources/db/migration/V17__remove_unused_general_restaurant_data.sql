-- 이용자·검수 자료가 연결된 업소와 다른 소스의 업소는 보존한다.
WITH retired_restaurants AS MATERIALIZED (
SELECT r.id FROM app.restaurant r
WHERE (r.phone_source_id IS NULL OR r.phone_source_id='00000000-0000-4000-8000-000000000100')
AND EXISTS (SELECT 1 FROM app.restaurant_external_id e WHERE e.restaurant_id=r.id
    AND e.source_id='00000000-0000-4000-8000-000000000100')
AND NOT EXISTS (SELECT 1 FROM app.restaurant_external_id e WHERE e.restaurant_id=r.id
    AND e.source_id<>'00000000-0000-4000-8000-000000000100')
AND NOT EXISTS (SELECT 1 FROM app.serving_scope s WHERE s.restaurant_id=r.id)
AND NOT EXISTS (SELECT 1 FROM app.evidence e WHERE e.restaurant_id=r.id)
AND NOT EXISTS (SELECT 1 FROM app.designation d WHERE d.restaurant_id=r.id)
AND NOT EXISTS (SELECT 1 FROM app.report p WHERE p.restaurant_id=r.id)
AND NOT EXISTS (SELECT 1 FROM app.restaurant_like l WHERE l.restaurant_id=r.id)
AND NOT EXISTS (SELECT 1 FROM app.information_report f WHERE f.restaurant_id=r.id)
AND NOT EXISTS (SELECT 1 FROM app.restaurant_manual_override m WHERE m.restaurant_id=r.id)
AND NOT EXISTS (SELECT 1 FROM app.restaurant_match_review m WHERE m.restaurant_id=r.id)
AND NOT EXISTS (SELECT 1 FROM app.restaurant_match_review m JOIN app.restaurant_external_id e
    ON e.source_id=m.source_id AND e.external_id=m.external_id WHERE e.restaurant_id=r.id)
AND NOT EXISTS (SELECT 1 FROM app.restaurant_match_audit m WHERE m.restaurant_id=r.id)
AND NOT EXISTS (SELECT 1 FROM app.restaurant_match_audit m JOIN app.restaurant_external_id e
    ON e.source_id=m.source_id AND e.external_id=m.external_id WHERE e.restaurant_id=r.id)
AND NOT EXISTS (SELECT 1 FROM app.restaurant_match_candidate c WHERE c.candidate_restaurant_id=r.id
    AND (c.source_id<>'00000000-0000-4000-8000-000000000100' OR c.status<>'PENDING'))
AND NOT EXISTS (SELECT 1 FROM app.ingestion_issue i JOIN app.ingestion_job j ON j.id=i.job_id
    WHERE i.restaurant_id=r.id AND j.source_id<>'00000000-0000-4000-8000-000000000100')
),

-- 삭제 대상은 한 문장 안에서 고정하고, 임시 테이블 권한을 추가하지 않는다.
removed_candidates AS (
    DELETE FROM app.restaurant_match_candidate WHERE candidate_restaurant_id IN (SELECT id FROM retired_restaurants) RETURNING candidate_restaurant_id
), removed_issues AS (
    DELETE FROM app.ingestion_issue WHERE restaurant_id IN (SELECT id FROM retired_restaurants) RETURNING id
), removed_source_records AS (
    DELETE FROM app.restaurant_source_record s USING app.restaurant_external_id e
    WHERE s.source_id=e.source_id AND s.external_id=e.external_id
        AND e.restaurant_id IN (SELECT id FROM retired_restaurants)
    RETURNING s.source_id,s.external_id
), removed_external_ids AS (
    DELETE FROM app.restaurant_external_id WHERE restaurant_id IN (SELECT id FROM retired_restaurants) RETURNING restaurant_id
)
DELETE FROM app.restaurant WHERE id IN (SELECT id FROM retired_restaurants);
