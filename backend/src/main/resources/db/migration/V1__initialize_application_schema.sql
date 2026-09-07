-- PostGIS 확장은 인프라 관리자만 초기화한다.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'postgis') THEN
    RAISE EXCEPTION 'PostGIS 확장을 먼저 초기화하세요.';
  END IF;
END;
$$;
