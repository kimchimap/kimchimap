# 데이터 모델 계약

> 2026-09-08 범위 정정: 국내산 사용 항목이 확인된 업소만 수집·공개한다. 원산지 없는 일반음식점 전체 수집은 중단한다. 과거 수집 엔진 설명은 기술 이력이며 현재 실행 허용을 뜻하지 않는다. 미등록 업소는 제보·검수로 등록한다. [결정 기록](decisions/0015-domestic-only-scope.md).

V1은 PostGIS 확장 존재를 검증한다. V2는 업소·외부 ID·scope·식재료·국가·법정/자발 표시 규칙·근거·불변 원산지 구성·철회·공개 포인터/이력·지정 테이블을 추가한다. V4는 수집 상태와 연락처, V5는 회원 식별·역할·상태·보안 변경 이력을 구현한다. 제보·미디어 모델은 후속 작업이다. 내부 ID는 UUID이며 외부 ID를 내부 PK로 쓰지 않는다. [결정](decisions/0005-origin-revisions.md)을 따른다.

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
| restaurant_like | member + restaurant UNIQUE |
| report/report_revision | 작성자, restaurant 후보, 상태, version, 불변 제출본, 검토 사유 |
| media/report_media | 소유자, 무작위 키, 원본/정제 구분, 처리·공개 상태, 크기·해시, 연결 소유권 |
| ingestion_job/checkpoint/quarantine | source, 범위, 상태, lease/fencing token, 페이지, 변경 해시, 오류·재개점 |
| match_candidate/manual_override | 외부 레코드와 후보, 검토 상태, 관리자 정정 범위·사유·유효성 |

origin_record 날짜는 observed_at(실제 관찰), source_updated_at(원문 갱신), collected_at(최초 수집), last_fetch_succeeded_at(다운로드 성공), reviewed_at, valid_from/valid_until로 구분한다. 모르면 NULL이고 추정하지 않는다. 모든 시각은 timestamptz, 원천이 날짜만 주면 원천 정밀도도 저장한다.

원산지 분류는 DOMESTIC / IMPORTED_SPECIFIED / IMPORTED_UNSPECIFIED / MIXED / UNKNOWN. 국가·혼합 관계는 별도 행으로 저장한다. DOMESTIC은 KR만, IMPORTED_SPECIFIED는 명시 국가, IMPORTED_UNSPECIFIED는 국가 없는 수입 구성요소, MIXED는 복수 구성요소이며 일부 국가 불명도 허용한다. UNKNOWN은 추정 국가 행이 없다. 혼합 비율 미표기는 NULL, 비율이 있으면 범위/합계 일관성을 검증하되 없는 비율을 계산하지 않는다.

불변 원산지 revision과 publication 포인터를 분리한다. 협회 지정, 표시판 관찰, 제보 접수, 납품 검증은 별개 evidence 종류다. 지정만으로 ingredient 행을 추론 생성하지 않는다. 업소에 isDomesticKimchi boolean만 두는 모델은 금지한다.

삭제: 탈퇴는 세션 폐기와 식별정보 최소화, 공개 근거의 보존 필요성·보유기간은 출시 전 결정한다. 개인정보 포함 증빙은 공개 권한과 원본 보관 권한을 분리하고 감사 이력을 임의 삭제하지 않는다.

## 업소 연락처

`restaurant.phone_display`는 공식 업소 연락처의 표시 원문, `phone_number`는 구분자를 제거한 연결용 번호, `phone_source_id`는 출처다. 세 값은 함께 존재하거나 모두 null이다. 출처의 재게시 허용 여부를 상세 조회에서도 확인한다. 원천 기록에는 제공된 `TELNO`를 보존하며 잘못된 형식은 품질 문제로 기록하고 전화 링크로 공개하지 않는다. 수집기가 관리자 전화번호 정정을 무조건 덮어쓰지 않도록 필드별 정정 보호를 적용한다. 업소 연락처 제공은 식재료 원산지 확인이나 실제 통화 성공을 뜻하지 않는다.

V6는 bookmark의 회원/업소 유일 키와 media의 소유자·무작위 저장 키·크기/해상도·해시·TEMPORARY/ATTACHED/DELETING 상태를 추가한다. 공개 플래그는 ATTACHED와 관리자 개인정보 검토 시각/행위자 없이는 켤 수 없다. 현재 API는 공개 플래그를 변경하지 않으며 제보 검수 연결은 후속 작업이다.

제보 구현: 일곱 번째 마이그레이션은 report/current_revision, 불변 report_revision/report_media/report_review, 24시간 report_request를 추가한다. 검수 이력에는 공개 반영한 품목과 개인정보 검토한 사진 목록을 남긴다. 내부 USER_REPORT 출처는 제보별 공개 동의에 근거하며 외부 데이터 허가와 별개다. 상세 결정은 [제보 공개 반영](decisions/0010-report-publication.md)을 따른다.

여덟 번째 마이그레이션: ingestion_request는 관리자 요청 키·해시·작업·24시간 만료를 보관하며 ingestion_request_audit는 실행자·소스·사유·예산의 불변 감사를 남긴다. 제보·수집 요청의 만료 인덱스를 추가했다.

아홉 번째 마이그레이션: origin_correction은 명시적 철회 대상 UUID 목록, 이전 공개 버전, 실행자·사유·시각의 불변 감사다. 기존 origin_record와 origin_component는 변경하지 않으며 origin_withdrawal과 공개 이력을 같은 트랜잭션에 추가한다.

열 번째 마이그레이션은 data_source.designation_allowed(기본 false), designation.version/reviewed_at와 불변 designation_revision을 추가한다. restaurant_match_review는 source/external별 정규화 관찰본·원래 수집 시각·작업·버전·결정 상태를 보관한다. restaurant_match_audit는 결정 당시 관찰본과 관리자·사유를 불변 보존한다. 현재까지 실제 지정 소스를 활성화하지 않았다.

V12는 기존 bookmark를 restaurant_like로 이름 변경하고 업소별 합계 조회 인덱스를 추가한다. 회원·업소 유일성과 기존 데이터는 보존한다. 새로운 별도 즐겨찾기 저장소를 두지 않는다.

V13은 미등록 위치 제보를 위해 report.restaurant_id를 승인 전까지 비워 둘 수 있게 한다. 승인 상태에는 업소 연결을 강제하는 CHECK 제약을 둔다. 신규 이름·주소·좌표는 불변 report_revision JSON에 보관하며 수정은 새 revision이다. 관리자 승인 시 restaurant Service에서 생성하고 report·origin·검토 기록과 같은 트랜잭션을 사용한다. 같은 이름·주소의 승인 경합은 DB 트랜잭션 advisory lock 후 중복 확인으로 차단한다. 유사한 이름·주소를 자동 병합하지 않는다.

V14는 실제 승인 인증일 조회를 위한 report_review의 승인 revision 식별자·시각 부분 인덱스를 추가한다. 기존 origin.reviewed_at은 별도로 보존하고 report_review.created_at과 혼동하지 않는다.

V15는 information_report·information_report_media·information_report_review를 추가한다. 원산지 제보와 별개로 오류 신고 본문·첨부 연결·처리 감사를 불변 보존한다. 회원별 요청 키 유일성과 본문 해시로 동일 요청 재전송을 처리하며, PENDING에서 RESOLVED/DISMISSED로 한 번만 전이한다. 관리자 처리에는 버전·사유·행위자·시각을 남긴다.

V16은 serving_scope.food_kind를 추가한다. 기존 품목은 UNKNOWN이며 이름으로 자동 분류하지 않는다. 관리자 확인을 거친 새 김치 품목은 KIMCHI다. 공개 함수는 KIMCHI 품목의 국내산 배추 또는 고춧가루 근거만 인정하며 쌀 등 다른 식재료는 공개 자격을 부여하지 않는다. [결정](decisions/0017-explicit-kimchi-scope.md).

V17은 과거 일반음식점 소스만 연결된 미사용 업소를 제거한다. 품목·근거·지정·제보·좋아요·정정·검수 연결, 다른 출처 식별자/전화번호/후보가 있으면 보존한다. 삭제 대상의 미검토 자동 후보·품질 오류·원천 좌표/전화번호·외부 식별자도 함께 제거하며 작업 집계와 불변 실행 감사는 유지한다. 한 SQL 문장에서 대상 집합을 고정하고 임시 테이블 권한을 추가하지 않는다.

수집 내부 관찰본은 originalSrid를 명시적으로 보존한다. 새 표준 입력에서 좌표계를 5174로 가정하지 않는다. 이전 검토 관찰본에 SRID가 없다면 실제 소스 명세를 확인하기 전 재반영하지 않는다. 현재 실제 대기 관찰본은 없으며 새 외부 소스 연결은 별도 작업이다.

## 업소 정정 이력 (V18)
restaurant_correction은 업소·행위자·이전 버전·변경 전후 JSON 스냅샷·사유·시각을 불변 저장한다. 변경한 필드만 restaurant_manual_override에 보호한다. 전화가 바뀌면 ADMIN_CORRECTION 출처로 표시하고, 변경하지 않은 전화 출처와 좌표 확인 상태를 보존한다. 폐업은 공개 제외, 재개는 원산지 공개 조건을 여전히 충족해야 한다. 정정은 원산지 인증 날짜를 갱신하지 않는다.
