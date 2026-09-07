# 국산김치맵 · kimchimap

음식점의 식재료 원산지를 메뉴·용도·출처·확인 날짜와 함께 검색하는 모바일 우선 웹 프로젝트입니다.

**현재는 서비스 구현 전 개발 하네스 단계입니다.** 실제 지도·로그인·제보·수집기는 아직 없으며 실제 원산지 데이터도 확보하지 않았습니다. 실행 가능한 하네스·로컬 인프라와 후속 개발 설계/수용 기준을 제공합니다. 운영 배포·CI/CD는 포함하지 않습니다.

## 시작

Git과 Python3.9 이상으로 저장소 루트에서 실행합니다.

```sh
./scripts/harness hooks-install
./scripts/harness verify-harness
./scripts/harness doctor
```

진단은 Java25·호환 Node 등 미충족 환경을 실패로 알립니다. 하네스 테스트에는 Java·외부 키가 필요하지 않습니다.

Docker가 준비되면 다음 명령으로 로컬 DB/Redis를 확인합니다.

```sh
./scripts/harness env-init
./scripts/harness infra-up
./scripts/harness infra-check
./scripts/harness infra-down
```

기존 .env와 데이터 볼륨을 보존합니다. PostGIS는 AMD64 이미지이므로 Apple Silicon에서 에뮬레이션을 사용합니다. 포트는 loopback에만 공개합니다.

`./scripts/harness verify`는 서비스 검사까지 포함하며 현재 미구현 단계에서는 실패합니다. `verify-harness` 성공을 전체 서비스 완료로 해석하지 않습니다. 실제 결과는 [검증 기록](docs/validation.md)을 확인하세요.

## 개발 진입점

[AGENTS.md](AGENTS.md) → [문서 목차](docs/index.md) → [후속 구현 계획](docs/plans/active/service-implementation.md).
[전체 명령](docs/local-development.md), [Git 규칙](docs/git-workflow.md), [기여 안내](CONTRIBUTING.md), [보안 제보](SECURITY.md).

frontend/는 React, backend/는 Spring Boot를 위한 경계입니다. 현재는 지침만 있습니다. tools/harness/는 실행 검사기, infra/는 로컬 PostgreSQL/PostGIS·Redis, docs/는 설계·수용 기준·의사결정입니다.

원격 dev와 main/dev GitHub ruleset 보호를 생성하고 유효 규칙을 확인했습니다. 작업별 구현·검증·dev PR 병합을 진행합니다. 오픈소스 라이선스는 미선택이며 공개 운영 전 명시적인 선택이 필요합니다. 코드·데이터·이미지의 이용 조건은 별도로 관리합니다.
