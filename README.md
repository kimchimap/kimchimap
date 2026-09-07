# 국산김치맵 · kimchimap

식재료 원산지를 메뉴·용도·출처·확인 날짜와 함께 검색하는 모바일 우선 웹 프로젝트입니다.

현재 **애플리케이션 기반 구현** 단계입니다. Spring MVC 서버, React 안내 화면, PostgreSQL/PostGIS·Redis 연결, Flyway 초기화, OpenAPI 타입 생성과 테스트가 동작합니다. 업소 검색·로그인·제보·수집은 후속 작업이며 실제 원산지 데이터는 아직 없습니다.

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
./scripts/harness verify-unit application-foundation
```

단위 검증은 하네스·환경·포맷·타입·실제 DB/Redis·API 타입·모바일/PC E2E·양쪽 빌드를 포함합니다. `./scripts/harness verify`는 전체 서비스의 후속 검사까지 포함하므로 외부 수집 계약 미구현 단계에서는 실패합니다. 이를 서비스 전체 완료로 해석하지 않습니다.

[개발 지침](AGENTS.md) → [문서 목차](docs/index.md) → [후속 계획](docs/plans/active/service-implementation.md).
[실행 명령](docs/local-development.md), [검증 기록](docs/validation.md), [기여 안내](CONTRIBUTING.md), [보안 제보](SECURITY.md).

main/dev에 PR 필수·강제 push/삭제 금지 ruleset을 적용했습니다. 작업별 검증 후 dev PR 병합을 진행하며 main 릴리스·운영 배포·CI/CD는 범위 밖입니다. 오픈소스 라이선스는 미선택이며 코드·데이터·이미지 이용 조건은 별도로 관리합니다.
