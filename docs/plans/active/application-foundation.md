# P01/P02: 실행 가능한 애플리케이션 기반

- 목표: Java25/Node LTS/Gradle 고정, 실제 Spring MVC/React 실행·DB/Redis 연결·OpenAPI·기본 보안·단위/통합/UI 검증.
- 현재 브랜치: chore/application-foundation.
- 기준 origin/dev: c146f1b9f0bf44e20936b0a2fcf29d92f59a8c28.
- 수용 기준: 공식 checksum 검증, Wrapper 실행, 실제 DB/Flyway 초기화와 Redis ping, 한국어 상태 화면, 보안/오류 계약 테스트, 생성 API 타입 비교, 모바일/PC E2E, 서비스 build. 외부 키 없이 실행.
- 제외: 원산지/인증/제보/수집 도메인은 다음 작업. 미구현 완료 표시 금지.
- 중요한 결정: 작업별 verify-unit을 추가하되 verify의 전체 검사와 미구현 실패는 유지. 아직 없는 후속 기능 검사와 현재 작업의 회귀 실패를 구분한다. 검사 삭제·skip 없음.
- 완료: 하네스 PR #1 병합, 보호 규칙 적용·25개 회귀 통과, 공식 런타임 다운로드 checksum 확인.
- 남은 작업: 앱 기반 구현과 전 검증, 문서 최신화, PR 병합·이전 브랜치 정리.
- 외부 차단: 사용자가 카카오/공공 API 미준비를 확인. 현재 작업은 키 없이 진행.
- 다음 실행: 고정 런타임 로더와 실제 Gradle/프론트 빌드 구성.
