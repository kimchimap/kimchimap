# 요구사항 추적

원 요청 1~17절을 아래 ID에 대응한다. 서비스 수용 기준은 모두 미구현이며 이 표의 존재가 테스트 통과를 뜻하지 않는다. machine-readable 목록은 requirements.json, 세부 자동 테스트는 testing.md다.

| ID | 범위 | 세부 수용 기준 |
| --- | --- | --- |
| R01 | 제품 범위·한국어·오픈소스 경계 | [구현/테스트/실연동/운영 상태 분리; 제외 기능 미추가](product.md) |
| R02 | 최신 안정 버전·호환성·고정 | [공식 출처/확인일/호환 build 증거; Preview/latest 금지](versions.md) |
| R03 | 모노레포·라이선스 | [frontend/backend 분리, 라이선스 임의 선택 금지, 서드파티 장부](product.md) |
| R04 | 계층·트랜잭션 | [ArchUnit; Entity API 노출/직접 Repository/순환 차단; OSIV off](architecture.md) |
| R05 | 원산지 모델·검색 의미 | [동일 scope AND/국가 OR; 혼합/미확인/분쟁/만료; 날짜 불변](data-quality.md) |
| R06 | 실제 허용 수집 | [명세 기반 client/계약; checkpoint/멱등/lease/429/누락/override 보존](ingestion.md) |
| R07 | PostGIS·지도 | [5174 실제 변환, 공간 인덱스·미터·bbox/반경·상한·SDK fallback](architecture.md) |
| R08 | 로그인·세션 | [PKCE/state/nonce/JWT·Redis 원자 회전·재사용·즉시 폐기·CSRF](security.md) |
| R09 | 제보·증빙·검수 | [소유권·이미지 재인코딩·비공개·동시 검수·불변 승인](security.md) |
| R10 | 전체 프론트 UX | [모바일/PC 필수 화면·필터·상태·접근성·서버 타입 생성](frontend.md) |
| R11 | API·공통 보안 | [HTTP/한국어 오류·상한·trace·인가·SSRF·prod fail closed](api.md) |
| R12 | 로컬 재현 | [실제 PostGIS/Redis·권한 분리·환경 진단·볼륨/.env 보존](local-development.md) |
| R13 | 실행 하네스 | [실제 도구·전체 검증·하네스 회귀 테스트·미구현 실패](local-development.md) |
| R14 | 한국어 Git | [훅/PR 공통 검증·허용 명칭·영어 편법·의미 리뷰](git-workflow.md) |
| R15 | 브랜치·보호 | [최신 dev·실제 remote ref 차단·병합/OID cleanup·보호 dry-run/apply](git-workflow.md) |
| R16 | 필수 테스트 | [단위/실제 DB/Redis/계약/E2E/보안/성능, 외부 smoke 분리](testing.md) |
| R17 | 단계·완료 보고 | [전 범위 수용 기준·명령 결과·외부 차단·다음 재개점·9항목 보고](plans/active/service-implementation.md) |
