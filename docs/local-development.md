# 로컬 개발과 검증

Git·Python 3.9 이상·Docker가 필요하다. `toolchain-install`은 사용자 캐시에 고정된 Java 25·Node 24·pnpm 12를 설치하고 시스템 기본값을 바꾸지 않는다. 버전·이미지 digest는 [버전 기록](versions.md)을 따른다.

PostGIS 자체는 ARM64에서 사용할 수 있다. 선택한 `postgis/postgis:18-3.6` 이미지가 AMD64이므로 Apple Silicon에서는 에뮬레이션을 사용한다. Linux ARM64도 해당 에뮬레이션이 필요하다. Redis는 현재 CPU에 맞는 이미지를 사용한다.

## 처음 실행

```sh
./scripts/harness toolchain-install
./scripts/harness hooks-install
./scripts/runtime pnpm install --frozen-lockfile --ignore-scripts
./scripts/harness env-init
./scripts/harness infra-up
./scripts/harness doctor
./scripts/harness infra-check
```

`env-init`은 `.local/infra.env`에 무작위 DB·Redis 비밀번호를 만들고 기존 파일을 보존한다. `.local`은 Git에서 제외한다. DB는 127.0.0.1:54329, Redis는 127.0.0.1:63799만 바인딩한다. Redis는 인증을 요구한다. `infra-down`은 볼륨을 삭제하지 않는다. 기존 DB의 비밀번호는 파일만 바꿔서 변경되지 않는다.

빈 DB에서는 이미지가 PostGIS 확장을 만들고 `infra/init-roles.sh`가 앱·마이그레이션 계정을 분리한다. Flyway는 `kimchimap_migrator`, 앱은 `kimchimap_app`을 사용한다. 기존 마이그레이션 수정 대신 후속 마이그레이션을 작성한다.

다음 명령을 서로 다른 터미널에서 실행한다. 프론트는 반드시 http://localhost:5173 으로 연다.

```sh
./scripts/harness dev-backend
./scripts/harness dev-frontend
```

기본 백엔드는 8080이다. 다른 서비스가 사용 중이면 그 서비스를 종료하지 말고 다음처럼 맞춘다.

```sh
SERVER_PORT=18080 ./scripts/harness dev-backend
API_PROXY_TARGET=http://127.0.0.1:18080 ./scripts/harness dev-frontend
```

서버 종료는 각 터미널의 Ctrl+C, 인프라 종료는 `./scripts/harness infra-down`이다.

## 키와 실제 카카오 연동

권한 600인 `.local/integrations.env`에 KAKAO_JAVASCRIPT_KEY, KAKAO_CLIENT_ID(REST API 키), KAKAO_CLIENT_SECRET을 설정한다. 런타임은 Git 공통 디렉터리의 설정을 허용 목록으로 파싱하며 셸로 실행하거나 값 전체를 출력하지 않는다. JavaScript 키만 브라우저에 전달한다. 서버 키·시크릿은 VITE 변수에 넣지 않는다.

카카오 앱에서 지도·로그인·OIDC를 활성화하고 도메인 `http://localhost:5173`, callback `http://localhost:5173/api/v1/auth/callback/kakao`를 등록한다. 127.0.0.1은 다른 Origin이다. `/login`에서 카카오로 이동하고 성공 후 토큰 없는 `/auth/complete`에서 서비스 세션을 복구한다. 키 미설정 시 지도 안내·목록은 사용 가능하고 로그인 시작은 503으로 거부된다.

`./scripts/harness smoke-external`은 실제 SDK·지도 타일·공식 인가 진입을 PC/모바일에서 확인한다. 키 누락이나 외부 실패는 실패한다. 외부 trace·스크린샷·전체 URL을 저장하지 않는다. 이 검사는 사용자 동의·코드 교환 완료와 초기 원산지 수집을 검증하지 않는다.

## 검증 명령

먼저 `./scripts/runtime pnpm --dir frontend exec playwright install chromium`으로 브라우저를 설치한다. `./scripts/harness verify`는 아래 품질 검사를 한 번에 실행하며 외부 키는 필요 없다.

| 명령 (`./scripts/harness` 뒤) | 역할 |
| --- | --- |
| doctor / infra-check | 도구·훅·런타임·실제 DB/Redis 확인 |
| format-check / lint-harness / test-harness / verify-harness | 문서·Python·JSON·Git 훅·임시 저장소 회귀 |
| format-backend / format-frontend / lint / typecheck | 포맷·린트·타입 검사 |
| test-backend / test-frontend | 백엔드 단위·프론트 테스트 |
| test-integration / test-contract / test-performance | 실제 DB·Redis·통제 OIDC, 내부 수집 어댑터 계약, 2만 건 공간 실행계획 |
| api-generate / api-check | OpenAPI 생성·타입 변경 확인 |
| test-e2e | 모바일/PC 오류·SDK 경계·화면 API mock 검증 |
| test-e2e-integration | 실제 서버·임시 저장소의 로그인→사진 제보→승인→공개→좋아요→신고 |
| build / build-backend | 프론트 번들·백엔드 실행 JAR |
| smoke-external | 실제 카카오 지도·인가 진입 별도 확인 |
| ingest-run / ingest-status | 소스 자격 검사·거부, 기존 실행 이력 조회 |
| branch-start / commit-check / pr-check / pr-prepare | [Git 작업 절차](git-workflow.md) |
| branch-cleanup / protection-check / protection-plan | 병합 OID 확인 정리·보호 조회·변경 계획 |

브라우저 검증은 18080·5173 포트가 비어 있어야 한다. 기존 서버를 재사용·자동 종료하지 않는다. 실제 연결 검증은 PC·모바일 각각 임시 PostGIS·Redis·사진 저장소와 새 서명키를 사용한다. 테스트 공급자·관리자 초기화는 테스트 클래스에만 있어 bootRun/bootJar에서는 사용할 수 없다. 모의 성공과 외부 실연동은 별도다.

## 자료와 관리

허용된 초기 원산지 자료·실사용 어댑터는 아직 없다. 일반음식점 전체 수집 호출부는 제거했다. `ingest-run`이 차단되는 것은 정상이며 키·설정만 바꿔 수집을 재개할 수 없다. 과거 공공데이터 키는 파일에 남아 있어도 런타임에 전달하지 않는다. [수집 정책](ingestion.md)과 [연동 현황](integrations-status.md)을 따른다.

미등록 가게는 지도 위치 선택→제보하기에서 등록한다. 승인 전 공개하지 않는다. `/likes`는 내 좋아요, `/information-reports`는 내 신고, `/account`는 전체 로그아웃·탈퇴다. 관리자 화면은 서버의 ADMIN 역할이 필요하다. 지정·매칭도 이용 조건과 소스 자격을 통과해야 하며 현재 허가 없는 자료는 등록하지 않는다.

사진은 기본적으로 백엔드 실행 디렉터리 `.local/media`에 저장한다. `MEDIA_ROOT`를 웹 루트 밖 절대 경로로 지정하면 worktree 간 같은 저장소를 사용할 수 있다. 기존 파일을 자동 삭제하지 않는다. JPEG/PNG만 지원하며 원본·미승인 정제본은 권한 검증한다. 단독 업로드는 제보 접수가 아니고 24시간 후 미연결 정리 대상이다.

관리자 지정은 실제 로그인 뒤 회원 UUID·대상·사유를 검토하고 서버 실행자가 계획을 확인한다.

```sh
./scripts/runtime backend/gradlew -p backend memberAdmin -PmemberId='<회원 UUID>' -PmemberRole=ADMIN -PmemberReason='검수 담당자 지정'
```

승인된 대상에만 `-PmemberApply=true`를 추가한다. USER로 되돌리는 것도 명시적으로 실행한다. 실제 회원 권한은 이번 자동 작업에서 변경하지 않았다.

## 세션 설정

로컬 JWT_PRIVATE_KEY_PATH 미설정 시 메모리 RSA 키를 사용한다. 재시작 후 Access Token은 무효이며 유효한 Refresh 세션으로 갱신한다. 운영은 RSA 개인 JWK 경로·JWT_KEY_ID가 필요하고 이전 공개키는 JWT_VERIFICATION_KEYS_PATH에 JWK Set으로 제공한다. 기본 Access Token은 10분, 세션 절대 수명은 14일이며 운영 안전 검증을 적용한다. SEARCH_CURSOR_SECRET은 운영에서 32바이트 이상이다.

갱신·로그아웃은 `/api/v1/auth/csrf`의 토큰과 동일 Origin을 요구한다. 토큰은 브라우저 메모리·HttpOnly 쿠키를 분리하며 사용자 캐시는 로그아웃 때 제거한다. 자세한 키 교체·장애 정책은 [보안](security.md)을 따른다.

## 카카오 로그인 KOE205 해결

`openid`를 요청하는 현재 구성에서는 카카오 개발자 콘솔의 OpenID Connect가 ON이어야 한다. 카카오 로그인 사용 설정과 별도 항목이므로 둘 다 확인한다. OFF일 때는 KOE205가 발생할 수 있다. 앱 관리자의 승인을 받아 활성화한 뒤 서비스 로그인 화면에서 새 요청으로 재시도한다. 오류를 피하려고 ID 토큰·nonce 검증을 제거하지 않는다. [공식 오류 문서](https://developers.kakao.com/docs/en/kakaologin/trouble-shooting)를 참고한다.
