# 국산김치맵 · kimchimap

식재료 원산지를 메뉴·용도·출처·확인 날짜와 함께 검색하는 모바일 우선 웹 프로젝트입니다.

현재 **제보·관리자 검수 구현** 단계입니다. Spring MVC 서버, React 안내 화면, PostgreSQL/PostGIS·Redis 연결, Flyway 초기화, OpenAPI 타입 생성과 테스트가 동작합니다. 업소 상세·식재료/국가 카탈로그 API와 불변 원산지 기록의 공개 판정도 구현했습니다. DB 지도 영역·반경·원산지 조합 검색을 구현했습니다. 공식 일반음식점 수집기로 제한 범위 200건 중 199개 업소를 저장하고 공개 조회를 확인했습니다. 전화번호·출처·전화 연결 화면도 제공합니다. 서비스 JWT·Redis 세션과 갱신·폐기 API를 구현했습니다. 카카오 로그인 연결과 브라우저 인증을 구현했으며 실제 계정 로그인은 별도 확인 중입니다. 즐겨찾기와 비공개 JPEG/PNG 사진 업로드도 구현했습니다. 제보 작성·수정·철회와 관리자 승인·반려·보완 요청, 원산지 공개 반영을 구현했습니다. 지도 UI와 관리자 정정·수집 관리 화면은 후속 작업이며 실제 원산지 데이터는 아직 없습니다.

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
./scripts/harness verify-unit reports-review
```

단위 검증은 하네스·환경·포맷·타입·실제 DB/Redis·API 타입·모바일/PC E2E·양쪽 빌드를 포함합니다. `./scripts/harness verify`는 외부 API 합성 계약과 공간 실행계획을 포함한 현재 전체 품질 검사를 실행합니다. 검사가 통과하더라도 아직 구현하지 않은 로그인·제보·전체 지도 화면까지 완료했다는 의미는 아닙니다.

[개발 지침](AGENTS.md) → [문서 목차](docs/index.md) → [후속 계획](docs/plans/active/service-implementation.md).
[실행 명령](docs/local-development.md), [검증 기록](docs/validation.md), [기여 안내](CONTRIBUTING.md), [보안 제보](SECURITY.md).

main/dev에 PR 필수·강제 push/삭제 금지 ruleset을 적용했습니다. 작업별 검증 후 dev PR 병합을 진행하며 main 릴리스·운영 배포·CI/CD는 범위 밖입니다. 오픈소스 라이선스는 미선택이며 코드·데이터·이미지 이용 조건은 별도로 관리합니다.

## 허용된 업소 기본정보 수집

일반음식점 API 활용 승인과 로컬 Decoding 키 등록 후 `./scripts/harness ingest-run`을 실행합니다. 기본 최대 2페이지이며 `./scripts/harness ingest-status`로 부분 수집·검토 필요 건수를 확인합니다. [소스와 이용 조건](docs/data-sources.md), [재개·예약 정책](docs/ingestion.md)을 확인하세요. `/restaurants/:id/contact`에서 공개 업소의 전화번호를 확인할 수 있습니다. 공공 업소 데이터로 원산지를 추정하지 않습니다.
