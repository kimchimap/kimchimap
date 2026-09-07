#!/bin/sh
set -eu
# 빈 볼륨 초기화 시에만 역할을 생성한다.
psql -v ON_ERROR_STOP=1 --username postgres --dbname kimchimap \
  --set=migrator_password="$MIGRATOR_PASSWORD" --set=app_password="$APP_PASSWORD" <<'SQL'
CREATE ROLE kimchimap_migrator LOGIN PASSWORD :'migrator_password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE kimchimap_app LOGIN PASSWORD :'app_password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON DATABASE kimchimap FROM PUBLIC;
GRANT CONNECT ON DATABASE kimchimap TO kimchimap_migrator, kimchimap_app;
CREATE SCHEMA app AUTHORIZATION kimchimap_migrator;
GRANT USAGE ON SCHEMA app TO kimchimap_app;
ALTER DEFAULT PRIVILEGES FOR ROLE kimchimap_migrator IN SCHEMA app GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO kimchimap_app;
ALTER DEFAULT PRIVILEGES FOR ROLE kimchimap_migrator IN SCHEMA app GRANT USAGE, SELECT ON SEQUENCES TO kimchimap_app;
ALTER ROLE kimchimap_migrator SET search_path = app, public;
ALTER ROLE kimchimap_app SET search_path = app, public;
SQL
