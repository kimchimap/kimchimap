# 제보·검수 상태 전이

초기 제출은 PENDING이다. 모든 변경은 인증 주체·소유권·expectedVersion을 서버에서 검사한다.

| 현재 | 행위자·행동 | 다음·공개 영향 |
| --- | --- | --- |
| PENDING | 작성자 수정 | 새 제출 revision, PENDING 유지, 공개 영향 없음 |
| PENDING | 작성자 철회 | WITHDRAWN, 공개 영향 없음 |
| PENDING | 관리자 승인 | APPROVED, 승인 snapshot·감사·origin revision·publication을 단일 트랜잭션으로 반영 |
| PENDING | 관리자 반려 | REJECTED, 사유 필수, 공개 영향 없음 |
| PENDING | 관리자 보완 요청 | NEEDS_MORE_INFO, 요청 사유 필수 |
| NEEDS_MORE_INFO | 작성자 보완 제출 | 새 revision을 PENDING으로 제출, 이전 검토 기록 유지 |
| NEEDS_MORE_INFO | 작성자 철회 | WITHDRAWN |
| APPROVED/REJECTED/WITHDRAWN | 작성자 기존 내용 수정 | 거부, 필요한 경우 별도 신규 제보 |

동시 검수는 version compare-and-update 또는 행 잠금으로 하나만 성공하고 나머지는 409다. 상태·공개 반영·감사 중 하나라도 실패하면 모두 rollback. 이미 승인된 내용은 사용자 변경으로 덮어쓰지 않는다. 관리자 정정도 사유·대상 revision이 있는 신규 기록으로 남긴다.

공개 이미지 승격은 제보 승인과 별도 개인정보 검토 조건을 충족해야 한다. 승인만으로 미정제 원본을 공개하지 않는다. 타인 미디어 연결과 공개 여부 임의 입력을 거부한다. 실제 납품 검증으로 격상하는 안내를 하지 않는다.

구현 완료 범위: 제보 API·화면과 검수 API·화면, 사진별 공개 권한, 원산지 공개 판정의 트랜잭션 연결. 관리자 정정·지정·매칭과 전체 외부 로그인 실연동 검증은 후속 작업이다. [설계 결정](decisions/0010-report-publication.md)에 동의·멱등·공개 근거 범위를 기록했다.
