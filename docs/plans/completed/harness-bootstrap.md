# 하네스 구성 작업 기록

- 목표: 서비스 구현 전 후속 개발을 위한 실행 하네스와 전체 설계·수용 기준 구성.
- 범위: 지침·문서·로컬 인프라·검사기·Git 훅·PR/보호/정리 도구·하네스 테스트. 서비스 코드는 제외.
- 수용 기준: 키 없이 하네스 실행, 실제 Git 성공/거부 검증, 외부 조건·미구현 실패 구분, 볼륨/사용자 변경 보존.
- 현재 브랜치: chore/bootstrap-harness.
- 기준 로컬 dev 커밋: 0796eb5bdd6921df6145a382f4f8049d4de4089a. origin/dev는 없음.
- 완료한 작업: root/backend/frontend AGENTS, 전체 제품·모델·보안·수집·화면·API 설계, 17절 추적 목록, 단계별 계획, 실행 도구, 회귀 테스트, 실제 로컬 인프라 probe.
- 중요한 결정: ADR0001 하네스만, ADR0002 기존 Git 이력 보존, ADR0003 선택 이미지 AMD64 에뮬레이션. PostGIS 자체의 ARM64 지원과 구분.
- 실행한 명령과 결과: verify-harness 24개 통과, infra-up/check/down 성공, pr-check 성공, verify는 서비스 미구현으로 실패, doctor는 Java/Node 불충족, protection-check는 보호 없음으로 실패, protection-plan은 dry-run 성공. 상세는 docs/validation.md.
- 외부 차단: 키/허가/라이선스 미확정, 원격 dev/보호 없음. 관리자 권한은 확인됐지만 적용하지 않음.
- 남은 작업: 후속 서비스 계획 P01~P10, 실제 외부 smoke, 운영 준비 판단. CI/CD와 배포는 계속 범위 밖.
- 다음 작업: docs/plans/active/service-implementation.md P01의 환경·버전·원격 기준 확인.
- 자기 검토: 실제 훅 차단·Redis 인증·DB 역할 확인, 실패를 성공 코드로 바꾸지 않음. 개인 메모 파일과 .idea 보존, 커밋에 포함하지 않음.
- PR 상태: docs/pr-draft.md 초안의 내용 검사만 통과, 원격 push/PR 생성/병합 없음.
