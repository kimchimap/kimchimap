# 국산김치맵 · kimchimap

식재료 원산지를 메뉴·용도·출처·확인 날짜와 함께 검색하는 모바일 우선 웹 프로젝트입니다.

국내산 사용 항목이 확인된 업소만 수집·공개합니다. 미등록 업소는 이용자 사진 제보와 관리자 검수를 통해 등록합니다. 일부 메뉴의 국내산 기록을 업소 전체의 모든 재료로 확대하지 않습니다.

현재 **국내산 업소 공개 범위 수정** 단계입니다. 업소·원산지·공간 검색, 카카오 로그인 클라이언트, JWT·Redis 세션, 즐겨찾기·사진·제보·검수, 원산지 정정과 지정·매칭 관리가 구현되어 있습니다. 지도 화면은 별도 작업 브랜치에 보존했습니다. 미등록 업소 제보 흐름은 후속 구현 대상입니다.

**실제 원산지 데이터는 아직 확보하지 못했습니다.** 일반음식점 API는 인허가·기본정보만 제공하므로 국내산 사용 업소를 찾는 소스로 부적합합니다. 과거 199개를 저장·공개 조회한 작업은 범위 해석 오류였으며, 해당 자료를 국내산 업소 데이터로 사용하지 않습니다. 원산지 없는 업소 공개와 전체 음식점 수집을 차단했습니다. 현재 로컬 저장 자료 199개 중 국내산 공개 자격을 충족하는 업소는 0개입니다. 협회 자료는 이용 허락이 없어 수집하지 않습니다.

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
./scripts/harness verify-unit domestic-publication-scope
```

단위 검증은 하네스·환경·포맷·타입·실제 DB/Redis·API 타입·모바일/PC E2E·양쪽 빌드를 포함합니다. `./scripts/harness verify`는 외부 API 합성 계약과 공간 실행계획을 포함한 현재 전체 품질 검사를 실행합니다. 검사가 통과하더라도 실제 카카오 로그인·전체 지도 화면까지 완료했다는 의미는 아닙니다.

[개발 지침](AGENTS.md) → [문서 목차](docs/index.md) → [후속 계획](docs/plans/active/service-implementation.md).
[실행 명령](docs/local-development.md), [검증 기록](docs/validation.md), [기여 안내](CONTRIBUTING.md), [보안 제보](SECURITY.md).

main/dev에 PR 필수·강제 push/삭제 금지 ruleset을 적용했습니다. 작업별 검증 후 dev PR 병합을 진행하며 main 릴리스·운영 배포·CI/CD는 범위 밖입니다. 오픈소스 라이선스는 미선택이며 코드·데이터·이미지 이용 조건은 별도로 관리합니다.

## 데이터 수집 상태

일반음식점 전체 수집은 중단 대상입니다. `./scripts/harness ingest-run`은 원산지 선별이 불가능한 현재 소스의 실행을 거부하며, `./scripts/harness ingest-status`는 과거 실행 이력을 조회합니다. 키 발급·활용 승인만으로 국내산 판정에 적합한 데이터가 되는 것은 아닙니다. 허가된 원산지 자료의 실제 필드와 적용 범위를 확인한 뒤 수집기를 연결합니다. [소스 조사](docs/data-sources.md), [수집 정책](docs/ingestion.md)을 확인하세요.
