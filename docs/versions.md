# 버전 확인 기록

2026-09-07 공식 배포 메타데이터·호환 범위 조회 후 실제 설치·컴파일·테스트한 기반 버전이다. Preview·latest 태그·JDK preview는 사용하지 않는다. 세부 패키지는 pnpm lockfile과 Gradle BOM이 기준이다.

| 구성 | 고정 버전 | 공식 근거·선택 이유 |
| --- | --- | --- |
| Temurin OpenJDK | 25.0.4.1+1 LTS | [공식 릴리스](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.4.1%2B1), 플랫폼별 SHA256 검증, 시스템 Java 변경 없음 |
| Node | 24.20.0 LTS | [공식 배포](https://nodejs.org/dist/v24.20.0/), SHASUMS 검증, 지원 LTS 선택 |
| pnpm | 12.3.4 | [공식 배포](https://registry.npmjs.org/pnpm/12.3.4), packageManager 고정, 실행 버전 확인 |
| Spring Boot | 4.1.1 | [공식 호환](https://docs.spring.io/spring-boot/system-requirements.html), Java25/Gradle9 지원, 실제 서버·통합 테스트 |
| Gradle Wrapper | 9.7.1 | [공식 배포](https://services.gradle.org/versions/current), distribution과 Wrapper JAR SHA256 모두 확인 |
| springdoc | 3.1.1 | [공식 문서](https://springdoc.org/), Boot4.1.1 실제 OpenAPI 생성·타입 비교 |
| React / React DOM | 19.2.8 | [공식 버전](https://react.dev/versions), 실제 UI/Vitest/E2E |
| Vite / React plugin | 8.2.2 / 6.1.1 | [공식 가이드](https://vite.dev/guide/), 서로의 peer 범위 확인 |
| TypeScript | 5.9.3 | [공식 배포](https://registry.npmjs.org/typescript/5.9.3), openapi-typescript7.13.0의 ^5.x와 typescript-eslint8.69.0의 <6.1 공통 범위. 최신7/6을 비호환 조합으로 사용하지 않음 |
| React Router / TanStack Query | 8.3.1 / 5.102.8 | registry peer·Node 요구 확인, 실제 build |
| PostgreSQL / PostGIS | 18.6 / 3.6.4 | postgis/postgis18-3.6 digest 고정, 실제 DB 확인 |
| Redis | 8.10.1 | digest 고정, 실제 인증·데이터 왕복 테스트 |
| Spotless / Java format / ktfmt | 8.10.2 / 1.36.1 / 0.64 | Maven/플러그인 배포 확인, 실제 포맷 실행 |
| ArchUnit | 1.5.0 | Maven 배포 확인, 실제 계층 검사 |
| Vitest / Playwright | 5.0.0 / 1.63.0 | 실제 단위·모바일/PC E2E |

Flyway12.4.0·JUnit6.0.3·Testcontainers2.0.5는 Boot BOM을 따른다. 버전을 불필요하게 덮어쓰지 않는다. PostgreSQL 드라이버와 Spring Security도 BOM 관리다.

Gradle distribution SHA256: `acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a`.
Wrapper JAR SHA256: `7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d`.
플랫폼별 Java/Node URL·checksum은 tools/harness/toolchains.json에 고정한다. pnpm12 네이티브 실행 파일 연결을 위해 공식 pnpm install.js만 명시 실행하며 앱 의존 lifecycle script는 자동 허용하지 않는다.

PostGIS 자체는 ARM64를 지원하지만 선택한 공식 Docker 태그의 manifest는 AMD64만 제공한다. [ADR0003](decisions/0003-local-platform.md)과 이미지 digest를 참조한다. ARM64 네이티브 PostGIS로 실행했다고 보고하지 않는다.

인증 추가 확인(2026-09-07): Spring Boot 4.1.1 BOM이 선택한 Spring Security OAuth2 JOSE/Resource Server 7.1.1, Nimbus JOSE JWT 10.9.1을 사용한다. Gradle 실제 의존성 트리를 확인했으며 직접 버전을 덮어쓰지 않았다. 근거: [Spring Security JWT 공식 문서](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html). PKCE S256·issuer·JWKS·client_secret_post는 [카카오 OIDC 공식 명세](https://developers.kakao.com/docs/ko/kakaologin/rest-api)에서 확인했고 OAuth 연결은 다음 작업이다.
