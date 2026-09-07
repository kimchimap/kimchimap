# 수집 구현 지침

소스별 설정은 허가 상태, endpoint allowlist, 쿼터, 원천 주기, enabled=false 기본, 증분 cursor/전체 대조 주기를 포함한다. 확인되지 않은 endpoint/필드는 생성하지 않는다. 인증키 없이 인터페이스만 만든 것을 수집기 완료라고 표시하지 않는다.

수집 파이프라인: 실행 요청→DB job/lease 획득→원문 스키마 검사→허용 필드 추출→정규화→ID 매칭→영속 반영→checkpoint commit→공개 조회 확인. 공개 승격은 출처·근거·검수 정책을 통과한 원산지 기록만. 업소 기본정보 수집으로 원산지를 채우지 않는다.

Spring scheduler와 bounded executor, source+scope UNIQUE 활성 job을 사용한다. DB lease에 owner/fencing token/leaseUntil, heartbeat 갱신과 모든 checkpoint/쓰기에서 fencing 조건 확인. 단순 synchronized 금지. lease를 잃은 작업은 더 이상 쓰지 않는다. 페이지 처리와 checkpoint는 같은 DB 트랜잭션. 재시작은 마지막 커밋 이후, source/externalId 및 normalized content hash로 멱등.

연결 3초·응답 10초 기본, 크기 제한 10MiB, 최대 재시도 3회, 지수 백오프+jitter, 429는 Retry-After(초/HTTP date) 준수. 긴 대기는 executor 점유 대신 nextAttemptAt으로 재예약. 4xx 인증/명세 오류 무한 재시도 금지. 구조 변경은 quarantine과 관리자 경고, 누락 필드는 NULL 정책에 따라 검증. 치명적 변경은 해당 소스 중단.

작업 상태: QUEUED/RUNNING/SUCCEEDED/PARTIAL/FAILED/CANCELLED. total/read/accepted/changed/quarantined와 오류 요약, 전체 수집 완결 여부, 기간·checkpoint·다음 재개 시점 기록. 관리자 재실행은 202로 접수하고 사용자 요청 thread에서 수집하지 않는다.

좌표 원문과 원래 SRID 보존. 일반음식점 안내는 EPSG:5174이므로 `ST_Transform(ST_SetSRID(ST_MakePoint(:x,:y),5174),4326)` 후 국내 bounds·공식 기준점 오차를 검증한다. 4326 라벨만 붙이지 않는다. 누락/범위 오류/매칭 모호는 별도 상태. 필요시에만 조건 확인된 카카오 지오코딩, 저장/캐시 허락 전 결과 보관 금지.

전체 목록 누락은 별도 suspect 상태, 한 번 실패로 폐업·취소 처리 금지. 명시 취소와 전체 완료 대조를 구분한다. 관리자 override를 자동 수집이 덮어쓰지 않고 상충 후보를 남긴다. 주기는 공식 원천 주기 확인 후 결정, webhook은 실제 제공될 때만.

허용 응답 fixture에는 source·수집일·이용 조건·비식별 처리 근거를 sidecar로 기록한다. 원문 공개 허락이 없으면 테스트가 소유한 합성 fixture를 사용하되 실제 외부 명세 검증의 한계를 표시한다. 현재 실제 응답 fixture와 수집기는 없다.
