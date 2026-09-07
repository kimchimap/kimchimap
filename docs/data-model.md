# 데이터 모델 계약

아래는 구현해야 할 논리 모델이다. V1은 PostGIS 확장 존재를 검증하며 아래 도메인 테이블은 다음 작업에서 추가한다. UUID 또는 DB 식별자 중 일관된 방식을 구현 ADR로 정하고 외부 ID를 내부 PK로 쓰지 않는다.

| 테이블 개념 | 필수 내용·제약 |
| --- | --- |
| restaurant | 이름, 정규/원문 주소, 영업 상태, location, 좌표 검증 상태, version |
| restaurant_external_id | source_id + external_id UNIQUE, restaurant FK, 원천 상태 |
| serving_scope | restaurant FK, 메뉴/제공 품목, 용도(SIDE_DISH/STEW/OTHER/UNKNOWN), 원문명, 범위 명시 수준 |
| ingredient | 확장 가능한 code UNIQUE, 한국어명, 범주, 활성 상태 |
| labeling_rule | 식재료·품목·용도·법정/자발·제도 출처·시행일/종료일; 법정 목록 하드코딩 금지 |
| origin_record | scope FK, ingredient FK, 원문, 분류, evidence/source FK, 검수·유효·분쟁 상태, revision, supersedes FK |
| origin_component | record FK, country FK nullable, 국내/국가 명시 수입/국가 미표기 수입; 비율 nullable |
| country | ISO 국가 코드·한국어명; 미확인을 가짜 국가로 만들지 않음 |
| evidence/source | 종류, 출처 식별자, 허용된 원문 참조, 이용 조건 버전, 공개 범위 |
| designation | 제도, 외부 번호, 적용 품목·실제 기준 원문, 지정/만료/취소일, source FK |
| publication | scope + ingredient UNIQUE, 선택 revision 또는 분쟁 상태, 결정 이유, version |
| review/audit | 행위자, 시각, 대상, 이전/새 상태, 사유, 승인 당시 불변 snapshot |
| member/identity | USER/ADMIN, 정지/탈퇴, provider + provider subject UNIQUE; 이메일 병합 금지 |
| bookmark | member + restaurant UNIQUE |
| report/report_revision | 작성자, restaurant 후보, 상태, version, 불변 제출본, 검토 사유 |
| media/report_media | 소유자, 무작위 키, 원본/정제 구분, 처리·공개 상태, 크기·해시, 연결 소유권 |
| ingestion_job/checkpoint/quarantine | source, 범위, 상태, lease/fencing token, 페이지, 변경 해시, 오류·재개점 |
| match_candidate/manual_override | 외부 레코드와 후보, 검토 상태, 관리자 정정 범위·사유·유효성 |

origin_record 날짜는 observed_at(실제 관찰), source_updated_at(원문 갱신), collected_at(최초 수집), last_fetch_succeeded_at(다운로드 성공), reviewed_at, valid_from/valid_until로 구분한다. 모르면 NULL이고 추정하지 않는다. 모든 시각은 timestamptz, 원천이 날짜만 주면 원천 정밀도도 저장한다.

원산지 분류는 DOMESTIC / IMPORTED_SPECIFIED / IMPORTED_UNSPECIFIED / MIXED / UNKNOWN. 국가·혼합 관계는 별도 행으로 저장한다. DOMESTIC은 KR만, IMPORTED_SPECIFIED는 명시 국가, IMPORTED_UNSPECIFIED는 국가 없는 수입 구성요소, MIXED는 복수 구성요소이며 일부 국가 불명도 허용한다. UNKNOWN은 추정 국가 행이 없다. 혼합 비율 미표기는 NULL, 비율이 있으면 범위/합계 일관성을 검증하되 없는 비율을 계산하지 않는다.

불변 원산지 revision과 publication 포인터를 분리한다. 협회 지정, 표시판 관찰, 제보 접수, 납품 검증은 별개 evidence 종류다. 지정만으로 ingredient 행을 추론 생성하지 않는다. 업소에 isDomesticKimchi boolean만 두는 모델은 금지한다.

삭제: 탈퇴는 세션 폐기와 식별정보 최소화, 공개 근거의 보존 필요성·보유기간은 출시 전 결정한다. 개인정보 포함 증빙은 공개 권한과 원본 보관 권한을 분리하고 감사 이력을 임의 삭제하지 않는다.
