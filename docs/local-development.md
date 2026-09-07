# 로컬 개발과 명령

PostGIS 자체는 ARM64에서 사용할 수 있다. 여기서 AMD64 한정은 선택한 `postgis/postgis:18-3.6` 이미지의 manifest에 대한 설명이다. PostGIS 소프트웨어 전체의 아키텍처 제한이 아니다.

하네스는 macOS/Linux의 Git과 Python3.9+로 실행한다. pnpm 명령은 선택적인 루트 별칭이다. 현재 앱은 없으며 Java25·호환 Node 설치/Gradle Wrapper 생성은 후속 P01/P02다. macOS 기본 Java17과 Node22.13.1은 현재 후보 앱 버전의 요구를 충족하지 않는다.

```sh
./scripts/harness doctor
./scripts/harness hooks-install
./scripts/harness verify-harness
./scripts/harness env-init
./scripts/harness infra-up
./scripts/harness infra-check
./scripts/harness infra-down
```

env-init은 .local/infra.env의 DB·Redis 비밀번호를 무작위 생성하고 기존 .env/기존 설정을 보존한다. 비밀값은 출력하지 않는다. Redis 설정은 mode700 .local 아래 redis.conf에 저장하고 컨테이너 읽기용 파일 권한은 644다. Redis는 비밀번호 인증을 요구한다. compose는 이 파일을 명시 로딩한다. DB 127.0.0.1:54329, Redis 127.0.0.1:63799. PostGIS는 AMD64 에뮬레이션, Redis는 해당 CPU 이미지. Linux ARM64는 에뮬레이션 준비 필요. infra-down은 볼륨을 삭제하지 않는다. `down -v`나 파괴적 초기화 명령은 기본 제공하지 않는다.

PostGIS upstream 초기화 후 init-roles.sh가 앱 schema와 migrator/app 역할을 생성한다. 스크립트는 빈 볼륨에서만 실행된다. 비밀번호 파일만 바꿔 기존 DB 비밀번호가 변경된다고 생각하지 않는다. 앱은 kimchimap_app, Flyway는 kimchimap_migrator, 확장은 postgres 관리자가 생성한다. 초기 extension은 public schema, app 테이블은 app schema다. 데이터 보존 변경은 별도 마이그레이션과 복구 검토가 필요하다.

| 명령 (`./scripts/harness` 뒤) | 현재 실행/후속 연결 |
| --- | --- |
| doctor | 도구·Docker·Java25·Node·훅 진단, 불충족 exit2 |
| env-init / infra-up / infra-down / infra-check | 안전한 로컬 설정·Compose 시작/종료·실제 DB/Redis 확인 |
| hooks-install | 저장소 로컬 훅 설치, 다른 설정 덮어쓰기 금지 |
| format-check / lint-harness / test-harness / verify-harness | 텍스트 형식·Python AST/JSON/문서 링크/금지파일·하네스 회귀 테스트 |
| dev-backend / dev-frontend | 실제 gradlew bootRun / frontend dev 필요 |
| format-backend / format-frontend | 실제 Spotless/프론트 formatter 검사 필요 |
| lint / typecheck | frontend lint/typecheck task 필요 |
| test-backend / test-frontend | 실제 단위·UI 테스트 task 필요 |
| test-integration / test-contract | 실제 DB/Redis 및 외부 API 계약 task 필요 |
| test-e2e / test-performance | Playwright 및 공간 실행계획 task 필요 |
| api-generate / api-check | OpenAPI 생성 및 프론트 생성 타입/계약 비교 task 필요 |
| build / build-backend | 프론트 build / 백엔드 bootJar 필요 |
| ingest-run / ingest-status | 허용 소스 수집 관리 task 필요, 사용자 입력 URL 금지 |
| smoke-external | 키·허가 있는 실제 연동 별도 task 필요 |
| verify | 하네스+서비스 모든 핵심 검사, 현재 미구현이면 실패 |
| branch-start / commit-check / pr-check / pr-prepare | Git 절차 문서 참조 |
| branch-cleanup / protection-check / protection-plan | 기본 조회/dry-run, 명시 apply 분리 |

서비스 연결 명세는 tools/harness/commands.json이다. requires 파일만 만들면 성공하는 구조가 아니며 해당 task의 실제 종료 코드가 반영된다. 없는 task·누락 앱·실패 검사는 exit2로 보고한다. ingest task는 backend 단계에서 source allowlist와 관리자 권한을 확인하는 구현을 연결하며 현재는 네트워크 실행하지 않는다.

후속 로컬 frontend는 localhost:5173, backend는 localhost:8080으로 고정하고 Vite /api 프록시를 구성한다. 카카오 JS 키는 브라우저 공개 키로 취급하되 REST/Client Secret은 VITE_*에 넣지 않는다. OAuth redirect/도메인 등록·쿼터·실제 smoke는 별도 문서 결과로 남긴다.

전체 하네스 검사에는 앱 formatter/정적 타입 검사를 수행했다고 주장하지 않는다. 앱 구현 후 서비스 formatter와 linter를 제거하거나 verify-harness만으로 완료 처리하면 안 된다.
