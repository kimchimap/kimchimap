# 국산김치맵 · kimchimap

대한민국 전국에서 **국산 배추·국산 고춧가루·둘 다 국산** 김치를 사용하는 음식점을 찾는 모바일 우선 지도 서비스입니다. 같은 김치 품목·용도의 근거만 조합하고 출처·확인일·관리자 승인일·외부 지정일을 구분합니다.

지도·목록·상세, 카카오 로그인 클라이언트와 JWT·Redis 세션, 좋아요 수와 내 목록, 위치 선택 후 사진 제보·관리자 승인 등록, 정보 오류 신고·처리, 원산지 정정을 구현했습니다. 실제 지도 키가 없으면 안내와 목록으로 사용할 수 있습니다. 검증은 통제된 공급자·지도 경계 mock과 실제 PostgreSQL/PostGIS·Redis를 구분합니다.

**실제 원산지 초기 데이터는 아직 확보하지 못했습니다.** 이용 조건이 확인된 자료만 사용하며 허가가 확인되지 않은 협회 목록은 수집·저장·재게시하지 않습니다. 일반음식점 API에는 원산지 필드가 없으므로 서비스의 초기 업소 수집에 사용하지 않습니다. 과거 원산지 없는 업소 199건과 연결된 원천 자료를 삭제했고 현재 로컬 업소·원산지 데이터는 0건입니다. 운영 배포와 실제 카카오 사용자 동의·로그인 완료 검증은 수행하지 않았습니다.

## 로컬 실행

Git·Python3.9 이상·Docker가 필요합니다. macOS/Linux ARM64·AMD64 런타임을 지원하며 선택한 PostGIS 이미지는 Apple Silicon에서 에뮬레이션을 사용합니다.

```sh
./scripts/harness toolchain-install
./scripts/harness hooks-install
./scripts/harness doctor
./scripts/runtime pnpm install --frozen-lockfile --ignore-scripts
./scripts/harness env-init
./scripts/harness infra-up
```

각 터미널에서 다음을 실행하고 브라우저로 http://localhost:5173 을 엽니다.

```sh
./scripts/harness dev-backend
./scripts/harness dev-frontend
```

외부 키 없이 안내 화면과 서버 상태를 확인할 수 있습니다. 종료는 각 실행 터미널에서 Ctrl+C, 인프라는 `./scripts/harness infra-down`입니다. 기존 .env와 데이터 볼륨을 보존합니다. 로컬 DB/Redis 비밀값은 `.local/infra.env`, 외부 키는 `.local/integrations.env`에만 입력합니다. Git worktree에서도 같은 저장소의 로컬 설정을 안전하게 읽으며 값은 출력하지 않습니다.

## 검증

```sh
./scripts/runtime pnpm --dir frontend exec playwright install chromium
./scripts/harness verify
```

단위 검증은 하네스·환경·포맷·타입·실제 DB/Redis·API 타입·모바일/PC E2E·양쪽 빌드를 포함합니다. `./scripts/harness verify`는 수집 어댑터 내부 계약과 공간 실행계획을 포함한 현재 전체 품질 검사를 실행합니다. 검사 통과는 실제 외부 로그인·초기 데이터 확보·운영 준비 완료를 뜻하지 않습니다.

[개발 지침](AGENTS.md) → [문서 목차](docs/index.md) → [후속 계획](docs/plans/active/service-implementation.md).
[실행 명령](docs/local-development.md), [검증 기록](docs/validation.md), [기여 안내](CONTRIBUTING.md), [보안 제보](SECURITY.md).

main/dev에 PR 필수·강제 push/삭제 금지 ruleset을 적용했습니다. 작업별 검증 후 dev PR 병합을 진행하며 main 릴리스·운영 배포·CI/CD는 범위 밖입니다. 오픈소스 라이선스는 미선택이며 코드·데이터·이미지 이용 조건은 별도로 관리합니다.

## 데이터 수집 상태

일반음식점 전체 수집은 중단 대상입니다. `./scripts/harness ingest-run`은 원산지 선별이 불가능한 현재 소스의 실행을 거부하며, `./scripts/harness ingest-status`는 과거 실행 이력을 조회합니다. 키 발급·활용 승인만으로 국내산 판정에 적합한 데이터가 되는 것은 아닙니다. 허가된 원산지 자료의 실제 필드와 적용 범위를 확인한 뒤 수집기를 연결합니다. [소스 조사](docs/data-sources.md), [수집 정책](docs/ingestion.md)을 확인하세요.

지도 화면은 국산 배추·국산 고춧가루·둘 다 국산 필터를 제공한다. 지도의 빈 위치를 선택한 뒤 제보하기에서 가게와 표시판 사진을 제출하고 관리자 승인 후 공개한다. 좋아요 수·내 목록과 근거별 승인일·지정일을 구분한다. 허용된 실제 초기 원산지 데이터 확보 상태는 docs/integrations-status.md를 확인한다.

실제 화면–서버 연결은 `./scripts/harness test-e2e-integration`으로 검증합니다. PC·모바일 각각 임시 PostGIS·Redis·사진 저장소와 테스트 전용 OIDC 공급자를 시작하고 로그인→사진 제보→승인→지도 필터→좋아요→오류 신고를 수행합니다. 실제 카카오 계정이나 외부 원산지 자료는 사용하지 않습니다.
