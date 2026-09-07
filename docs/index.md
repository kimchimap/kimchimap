# 개발 문서 목차

첫 순서: [제품 범위](product.md) → [요구사항 추적](requirements.md) → [후속 구현 계획](plans/active/service-implementation.md).

- [아키텍처](architecture.md), [데이터 모델](data-model.md), [원산지 품질/판정](data-quality.md)
- [API 계약](api.md), [화면/UX](frontend.md), [보안](security.md), [제보 상태 전이](reports.md)
- [수집 설계](ingestion.md), [소스 조사](data-sources.md), [연동 현황](integrations-status.md)
- [로컬 실행](local-development.md), [테스트 수용 목록](testing.md), [실제 검증 결과](validation.md)
- [Git 절차](git-workflow.md), [버전/근거](versions.md), [서드파티 조건](third-party.md)
- [하네스 완료 기록](plans/completed/harness-bootstrap.md), [계획 양식](plans/template.md), [범위 결정](decisions/0001-harness-scope.md), [Git 기준점](decisions/0002-git-baseline.md), [CPU 지원 결정](decisions/0003-local-platform.md)

Codex는 루트 AGENTS.md와 작업 디렉터리의 지침을 함께 읽는다. [공식 AGENTS.md 안내](https://learn.chatgpt.com/docs/agent-configuration/agents-md)에 따라 짧은 실행 지침과 상세 문서를 분리한다. 외부 키·권한·데이터 허가를 지침만으로 획득했다고 해석하지 않는다.

현재 작업: [업소와 원산지 모델](plans/active/origin-model.md), [불변 기록 결정](decisions/0005-origin-revisions.md).

공간 검색: [결정](decisions/0006-spatial-search.md), [측정](performance.md), [진행 계획](plans/active/spatial-search.md).
