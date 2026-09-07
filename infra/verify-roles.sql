-- 검증 데이터는 트랜잭션 종료 시 제거한다.
BEGIN;
SET LOCAL ROLE kimchimap_migrator;
CREATE TABLE app.harness_probe (id integer PRIMARY KEY);
SET LOCAL ROLE kimchimap_app;
INSERT INTO app.harness_probe VALUES (1);
DO $$
BEGIN
  IF (SELECT count(*) FROM app.harness_probe) <> 1 THEN
    RAISE EXCEPTION '앱 DML 검증 실패';
  END IF;
  IF NOT ST_DWithin(
    ST_SetSRID(ST_MakePoint(127, 37), 4326)::geography,
    ST_SetSRID(ST_MakePoint(127, 37), 4326)::geography, 1
  ) THEN
    RAISE EXCEPTION 'PostGIS 거리 함수 검증 실패';
  END IF;
  BEGIN
    CREATE TABLE app.harness_forbidden (id integer);
    RAISE EXCEPTION '앱 계정의 DDL이 잘못 허용됨';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;
END;
$$;
ROLLBACK;
