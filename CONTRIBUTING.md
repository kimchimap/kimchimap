# 기여 안내

[개발 지침](AGENTS.md)과 [문서 목차](docs/index.md)를 읽고 `./scripts/harness doctor` → `hooks-install` → `verify-harness`를 실행한다. Python 3.9 이상이면 하네스 자체는 외부 패키지 없이 실행된다.

일반 작업은 최신 origin/dev에서 `./scripts/harness branch-start feat/english-name`으로 시작한다. main/dev 직접 변경·push·병합 금지. 현재 원격 dev는 없어 관리자 초기 설정 전에는 이 명령이 실패한다.

커밋과 PR은 `feat: 원산지 조건 검색을 구현`처럼 한국어로 작성한다. 본문·검증 결과도 한국어. [Git 절차](docs/git-workflow.md)와 PR 템플릿을 따른다. 의미 수준의 검토는 자동 판정을 대신할 수 없다.

서비스 구현 변경은 `./scripts/harness verify`, 하네스 범위 변경은 `verify-harness`를 실행하고 범위를 명시한다. 실패를 skip·검사 축소로 숨기지 않는다. 실제 응답·사진·키를 커밋하지 않는다. 합성 fixture는 test/demo를 표시하고 prod 자동 주입을 금지한다.

라이선스 선택 전이다. 프로젝트 라이선스나 권리자를 기여자가 임의 추가하지 않는다. 서드파티 코드·데이터·이미지 조건을 출처 장부에 기록한다. 공개 저장소라는 사실만으로 별도 데이터 재배포 권한이 생기지 않는다.
