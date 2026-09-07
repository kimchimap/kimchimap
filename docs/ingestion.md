# 수집 구현 지침

> 2026-09-08 범위 정정: 국내산 사용 항목이 확인된 업소만 수집·공개한다. 원산지 없는 일반음식점 전체 수집은 중단한다. 과거 수집 엔진 설명은 기술 이력이며 현재 실행 허용을 뜻하지 않는다. 미등록 업소는 제보·검수로 등록한다. [결정 기록](decisions/0015-domestic-only-scope.md).

소스별 설정은 허가 상태, endpoint allowlist, 쿼터, 원천 주기, enabled=false 기본, 증분 cursor/전체 대조 주기를 포함한다. 확인되지 않은 endpoint/필드는 생성하지 않는다. 인증키 없이 인터페이스만 만든 것을 수집기 완료라고 표시하지 않는다.

수집 파이프라인: 실행 요청→DB job/lease 획득→원문 스키마 검사→허용 필드 추출→정규화→ID 매칭→영속 반영→checkpoint commit→공개 조회 확인. 공개 승격은 출처·근거·검수 정책을 통과한 원산지 기록만. 업소 기본정보 수집으로 원산지를 채우지 않는다.

Spring scheduler와 bounded executor, source+scope UNIQUE 활성 job을 사용한다. DB lease에 owner/fencing token/leaseUntil, heartbeat 갱신과 모든 checkpoint/쓰기에서 fencing 조건 확인. 단순 synchronized 금지. lease를 잃은 작업은 더 이상 쓰지 않는다. 페이지 처리와 checkpoint는 같은 DB 트랜잭션. 재시작은 마지막 커밋 이후, source/externalId 및 normalized content hash로 멱등.

연결 3초·응답 10초 기본, 크기 제한 10MiB, 최대 재시도 3회, 지수 백오프+jitter, 429는 Retry-After(초/HTTP date) 준수. 긴 대기는 executor 점유 대신 nextAttemptAt으로 재예약. 4xx 인증/명세 오류 무한 재시도 금지. 구조 변경은 quarantine과 관리자 경고, 누락 필드는 NULL 정책에 따라 검증. 치명적 변경은 해당 소스 중단.

작업 상태: QUEUED/RUNNING/WAITING/SUCCEEDED/PARTIAL/FAILED/CANCELLED. total/read/accepted/changed/quarantined와 오류 요약, 전체 수집 완결 여부, 기간·checkpoint·다음 재개 시점 기록. 관리자 재실행은 202로 접수하고 사용자 요청 thread에서 수집하지 않는다.

좌표 원문과 원래 SRID 보존. 일반음식점 안내는 EPSG:5174이므로 `ST_Transform(ST_SetSRID(ST_MakePoint(:x,:y),5174),4326)` 후 국내 bounds·공식 기준점 오차를 검증한다. 4326 라벨만 붙이지 않는다. 누락/범위 오류/매칭 모호는 별도 상태. 필요시에만 조건 확인된 카카오 지오코딩, 저장/캐시 허락 전 결과 보관 금지.

전체 목록 누락은 별도 suspect 상태, 한 번 실패로 폐업·취소 처리 금지. 명시 취소와 전체 완료 대조를 구분한다. 관리자 override를 자동 수집이 덮어쓰지 않고 상충 후보를 남긴다. 주기는 공식 원천 주기 확인 후 결정, webhook은 실제 제공될 때만.

허용 응답 fixture에는 source·수집일·이용 조건·비식별 처리 근거를 sidecar로 기록한다. 원문 공개 허락이 없으면 테스트가 소유한 합성 fixture를 사용하되 실제 외부 명세 검증의 한계를 표시한다. 일반음식점 수집기와 합성 계약 fixture를 구현했다. 실제 응답은 테스트 대역으로 커밋하지 않는다.

## 일반음식점 실행 정책

`ingest-run`은 기본 INCREMENTAL, 100건씩 최대 2페이지를 처리한다. INCREMENTAL 최초 창은 최근 7일, 완료 후 다음 창은 마지막 완료 시각부터 2일 겹치며 현재 시각 미만이다. FULL은 시작일 필터 없이 같은 끝 시각을 사용한다. `PARTIAL`은 전체 완료가 아니며 동일 모드 재실행은 기존 끝 시각·페이지 크기와 다음 페이지를 유지한다. max-pages는 재실행마다 추가 페이지 예산이다. 총건수가 중간에 바뀌면 해당 작업을 실패시키고 새 창으로 다시 시작한다.

`/info`의 정렬·스냅샷 보장은 확인되지 않았다. 끝 시각 필터와 총건수 검사로 모든 누락을 증명하거나 제거할 수는 없다. 정기 전체 대조와 겹치는 증분 창으로 보완한다. 목록 누락은 missing_since_job_id로만 표시하고 폐업 처리하지 않는다. FULL 완료도 그 조회 범위의 페이지 처리 완료이며 실시간 전국 DB 일치 보장이 아니다. 원천 전체 약 230만 건의 수집을 소규모 실연동 검증으로 완료 표시하지 않는다.

스케줄러는 기본 꺼져 있다. `INGEST_SCHEDULING_ENABLED=true`와 허가된 ingestion_source.enabled=true를 함께 설정해야 동작한다. 주기는 ingestion_source.interval_seconds(최소 86400), 다음 실행은 next_run_at, 30일 이상 지난 전체 대조가 우선이다. 자동 하루 예산은 1,000페이지이며 미완료 작업은 다음 예정 주기에 재개한다. 설정은 로컬 운영자가 앱 DB 계정으로 변경하고 이후 관리자 화면에 연결한다. 예약된 실행은 단일 bounded executor, 다중 프로세스 상호 배제는 DB lease/fencing으로 보장한다.

페이지별 성공·오류는 수정·삭제가 거부되는 ingestion_job_event에 남는다. 세부 레코드 오류는 격리 코드와 최소 식별자로 기록하며 원문·비밀값을 로그에 남기지 않는다. SUCCEEDED는 해당 창의 페이지 처리 완료를 뜻한다. quarantined_count가 0이 아니면 모든 업소 반영 완료가 아니다. SOURCE_TOTAL_CHANGED는 재개 대신 새 작업을 요구한다. Retry-After가 7일을 넘으면 일찍 재시도하지 않고 수동 검토가 필요한 실패로 남긴다.

관리자 구현: `/api/v1/admin/ingestion` 아래 sources, jobs, jobs/{id}, jobs/{id}/events, jobs/{id}/quarantines, sources/{id}/runs를 제공한다. 재실행은 UUID 요청 키와 사유·모드·1~100페이지 예산을 받으며 202로 접수한다. 동일 키 재전송은 예산을 중복 추가하지 않는다. 관리자별 시간당 10회와 DB 잠금·불변 감사를 적용한다. 소스 허가와 키 설정이 없으면 거부한다. 원문과 내부 잠금 식별자는 응답에서 제외한다.

정기 수집 생성과 명시적 작업 실행을 분리했다. 정기 수집은 기존 조건을 유지하며 접수된 작업은 기본 실행기가 별도 스레드에서 처리한다. 테스트에서는 `app.ingestion.worker-enabled=false`로 두고 통제된 실행을 사용한다. 스케줄 등록 자체는 항상 활성화하여 사진·만료 요청 정리가 외부 정기 수집 설정에 종속되지 않게 했다.

매칭 격리 시 최소 정규화 관찰본을 저장하고 내용 변경 시 검토 버전을 증가시킨다. 이미 결정된 관찰본은 재수집이 덮어쓰지 않는다. 관리자는 후보 연결 또는 별도 업소 생성을 결정하며 업소 상태 변경은 RestaurantImportService를 통한다. 관찰본 없는 과거 후보는 다음 허용 수집까지 확정 불가다. 검토 후에도 원래 수집 작업의 격리 건수는 당시 결과로 보존된다. 대기 목록은 최대 50개이며 검토 완료 건은 빠진다.
