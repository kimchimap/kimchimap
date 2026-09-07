# Git 작업 규칙과 도구

main은 릴리스 기준, dev는 개발 통합이다. 일반 작업은 최신 origin/dev 기반 type/english-kebab-case 브랜치에서 수행한다. origin/dev는 기존 초기 커밋에서 생성했다. 기존 원격 초기 이력은 ADR0002대로 보존한다. 커밋이 전혀 없는 저장소만 기능 없는 최소 bootstrap 한 개로 기준을 만들 수 있으며 이 저장소에는 적용하지 않는다.

`./scripts/harness hooks-install`은 기존 다른 hooksPath를 덮어쓰지 않는다. pre-commit은 보호 브랜치/잘못된 브랜치와 staged whitespace, 빠른 하네스 문법·문서 정책 검사를 차단한다. commit-msg는 공통 policy.py를 사용한다. pre-push는 stdin의 실제 remote ref를 검사해 HEAD:main, 강제 push, main/dev 삭제를 차단한다. 로컬 훅은 우회 가능하므로 서버 보호가 필요하다.

커밋·PR 제목: `<prefix>[!]: <한국어 제목>`. feat/fix/docs/style/refactor/perf/test/build/chore/revert/release 허용. 제목은 대상·동작이 구체적이어야 한다. 본문도 한국어, 기술 명칭은 technical-terms.json에서 관리, 경로·URL·식별자 허용. 일반 영어 설명·한글 한 단어 편법·자동 Merge/Revert 기본 제목 차단. 정규식은 의미 수준 한국어·고유명사를 완벽하게 판단하지 못한다. 예외 명칭 추가는 검토와 회귀 테스트가 필요하다. 코드 블록·식별자 악용 여부는 사람이 검토한다.

일반 흐름:
1. 상태·설계 확인, 계획 작성. `branch-start feat/origin-filter`가 fetch 후 origin/dev에서 분기한다. 시작 SHA를 local config에 기록하지만 Git이 출발점을 완벽히 증명한다고 주장하지 않는다.
2. 작은 커밋과 관련 검증. 제목 파일은 `commit-check <file>`, PR은 `pr-check --title 'feat: 원산지 검색 기능을 구현' --body-file <file> --head feat/origin-filter`.
3. 병합 직전 최신 origin/dev를 작업 브랜치에 통합하고 verify. main/dev 로컬 merge 후 보호 브랜치 push 금지.
4. `pr-prepare --title ... --body-file ...`은 clean·훅·최신 base 포함·커밋/PR 메시지·전체 검증을 요구한다. 하네스 변경만 `--harness-only` 사용 가능하며 서비스 코드 변경은 차단한다. 준비 도구는 push/PR 생성/병합을 하지 않는다.
5. 검증 후 명시적으로 작업 브랜치 push와 draft PR을 준비할 수 있다. 현재 사용자는 검증된 작업의 dev PR 병합·임시 브랜치 삭제를 승인했다. dev로 squash merge, squash 제목/본문도 한국어로 편집. 승인 없는 자체 병합 금지.
6. `branch-cleanup feat/origin-filter --pr 123`으로 병합된 동일 저장소 PR·head OID·base dev·미커밋/추가 커밋을 확인한다. 다른 브랜치로 이동해야 한다. 기본 dry-run, 검토 후 --apply. 원격 삭제는 비교한 OID의 lease, 로컬 삭제는 검증 뒤에만. fork/main/dev 일괄 삭제 금지. squash에서는 ancestor만으로 판단하지 않는다.

릴리스는 같은 저장소 dev→main, release 제목, merge commit 방식. dev squash·삭제 금지. 사용자 명시 지시 없이 릴리스 PR/병합/배포를 실행하지 않는다.

보호 도구: `protection-check`는 effective rules와 classic protection을 읽는다. `protection-plan`은 기본 dry-run, PR·강제 push 금지·삭제 금지·대화 해결·bypass 없음·승인수0·dev squash/main merge 규칙 추가 계획을 출력한다. `--approvals 1`로 협업 강화 계획, `--apply`는 명시 승인 받은 뒤에만 실행한다. admin 권한·브랜치 존재·API 응답을 확인하고 생성 후 재조회 비교한다. 기존 규칙은 삭제/수정하지 않고 같은 이름에서 의도한 정책이 일치하면 보존하고 다른 정책이면 거부한다. 서버가 추가한 기본 필드·규칙 순서는 의미 비교한다. API/플랜이 지원하지 않으면 실패하고 부분 적용 여부를 재조회한다.

[GitHub rules API](https://docs.github.com/en/rest/repos/rules)의 branch별 allowed_merge_methods를 사용한다. 존재하지 않는 CI 상태 검사와 main linear-history를 추가하지 않는다. head=동일 저장소 dev와 한국어 의미는 서버 보장으로 간주하지 않고 PR 도구·리뷰로 검증한다. 웹에서 수정된 PR은 로컬 검증 뒤 바뀔 수 있다. 향후 CI는 같은 pr-check/verify를 호출할 수 있지만 이번에는 workflow를 만들지 않는다.

현재 main/dev ruleset을 적용하고 effective rules를 재조회했다. PR 필수·강제 push/삭제 금지·대화 해결·승인수0·main merge/dev squash를 확인했다. 한국어 의미와 main PR head=dev는 PR 검사·검토로 보완한다.

허용 공식 명칭에 Apple Silicon을 포함한다. 실제 CPU 관련 문서의 정상 PR 문장을 차단한 사례를 회귀 테스트로 추가했으며 일반 영어 설명 차단 정책은 유지한다.

2026-09-07 구현 착수: 보호 API가 추가하는 기본 필드로 인한 비교 실패를 수정하고 회귀 검증했다. 기존 규칙을 약화하거나 삭제하지 않으며 부분 적용 뒤 재실행할 수 있다.
