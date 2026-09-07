# 후속 서비스 구현 계획

상태: 구현 착수. 사용자 지시로 작업별 구현·검증·문서 갱신 후 dev PR 병합·임시 브랜치 삭제를 반복한다.
현재 브랜치: feat/reports-review. 기준 origin/dev: 9ab3a3f97e81354357577e31cf2f89969557a1fe. 하네스 PR #1 병합과 임시 브랜치 삭제를 완료했다.
목표: 국산김치맵 전체 서비스. 범위·금지·수용 기준은 product, requirements, testing 문서에 따른다.

| 순서 | 작업·완료 조건 | 실행 검증 |
| --- | --- | --- |
| P01 | 원격 dev/보호 계획 승인 후 기준 준비, 버전 재조회, Java25/Node 호환 런타임, 실제 Gradle Wrapper checksum·pnpm lock 고정 | doctor, protection-check |
| P02 | Boot MVC와 React 시작, local/test/prod 검증, 포맷·린트·테스트 task 실제 등록, OpenAPI 생성 | dev-backend, dev-frontend, format-check, lint, typecheck, api-check |
| P03 | Flyway·PostGIS·DML 역할, 카탈로그·scope·불변 origin 모델·조회·ArchUnit | test-backend, test-integration |
| P04 | bbox/반경/같은 scope 필터·안정 정렬·상한·실행계획 | test-integration, test-performance |
| P05 | 허용 데이터 명세 확인·어댑터·정규화·checkpoint/lease/재시도/격리/매칭/대조 | test-contract, ingest-run, ingest-status |
| P06 | Kakao OAuth·서비스 JWT·Redis family 회전·CSRF·폐기·권한 | test-backend, test-integration |
| P07 | 즐겨찾기·제보·파일 검증·관리자 동시 검수·불변 공개 승격 | test-integration |
| P08 | 모바일/PC 모든 화면·SDK 경계·필터·근거·오류·접근성 | test-frontend, test-e2e |
| P09 | 전체 기능 연결·보안/경쟁/장애/성능·README 재현 | verify |
| P10 | 키/허가 확보된 외부 smoke, 라이선스·보호 확인, draft PR 준비 | smoke-external, pr-prepare |

공통 수용 기준: 모든 서비스 테스트가 키 없이 실행됨, 실제 PostGIS/Redis 컨테이너 사용, 허용된 수집기는 실제 client 코드와 계약 테스트, 외부 성공은 호출→저장→조회 증거, 미구현/차단을 분리 보고. CI/CD와 배포는 추가하지 않는다.

진행: P01/P02 실제 앱 기반 구현·검증 및 PR #2 병합·브랜치 정리 완료. P03 PR #3 병합·정리 완료. P04 PR #4 병합·정리 완료. P05 PR #5 병합·정리 완료. P06a 서비스 세션과 P06b 카카오 로그인으로 작업을 분리했다. 남은 작업: P07~P10. 외부 차단: 키 저장 완료, 카카오 설정·공공 API 활용 승인 확인, 협회 자동 수집·재게시 허가 없음(사용자 확인), 카카오 실연동 미확인, 라이선스 미선택. P06a PR #6 병합·브랜치 정리 완료. P06b PR #7 병합·브랜치 정리 완료. P07a 즐겨찾기·증빙 이미지와 P07b 제보·검수를 나누어 구현한다. 명령 실제 결과는 validation.md에 누적하며 미실행을 성공 처리하지 않는다.

P07a PR #8 병합과 임시 브랜치 삭제를 완료했다. P07b 제보·검수 API와 화면을 구현하고 전체 검증을 통과했다. PR 준비를 진행한다. 세부 기록은 reports-review.md를 따른다.
