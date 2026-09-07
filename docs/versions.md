# 버전 확인 기록

PostGIS 자체는 ARM64에서 사용할 수 있다. 여기서 AMD64 한정은 선택한 `postgis/postgis:18-3.6` 이미지의 manifest에 대한 설명이다. PostGIS 소프트웨어 전체의 아키텍처 제한이 아니다.

확인일: 2026-09-07. 하네스 실행 버전과 향후 앱 선택 후보를 구분한다. 공식 웹 문서와 배포 registry에서 존재를 조회했다. 실제 앱 build 호환성은 미검증이며 P01/P02에서 재확인·고정한다. RC/Beta/Milestone/SNAPSHOT/Preview와 JDK preview 기능은 금지한다.

| 구성 | 선택/후보 | 공식 출처·이유·검증 상태 |
| --- | --- | --- |
| 하네스 Python | 3.9 이상, 현재 3.9.6 | 표준 라이브러리만 사용, macOS 기본 실행 가능. 앱 Python 의존 없음 |
| pnpm | 12.3.4 고정 | [공식 배포 registry](https://registry.npmjs.org/pnpm/latest) 조회. packageManager·lockfile 고정, lifecycle script 자동 실행 안 함 |
| Java | OpenJDK Temurin 25 LTS 후보 | [Adoptium](https://adoptium.net/temurin/releases/), 패치 API 조회는 403으로 미확정. 현재 설치는 Oracle Java 17.0.16이므로 앱 준비 불충족 |
| Spring Boot | 4.1.1 후보 | [공식 요구사항](https://docs.spring.io/spring-boot/system-requirements.html), Java17~26/Gradle9 지원. 4.2 milestone 제외 |
| Gradle | 9.7.1 후보 | [배포 API](https://services.gradle.org/versions/current), [Java25 호환](https://docs.gradle.org/current/userguide/compatibility.html). Java25 실행은 9.1 이상. Wrapper binary·distribution SHA256는 P02에서 실제 생성·검증 |
| springdoc | 3.1.1 후보 | [공식 문서](https://springdoc.org/), Boot4 계열용 3.x. 정확한 Boot4.1 build·생성 검증 전 설치 확정하지 않음 |
| React | 19.2.8 후보 | [공식 버전](https://react.dev/versions), [배포](https://registry.npmjs.org/react/latest) |
| Vite | 8.2.2 후보 | [공식 가이드](https://vite.dev/guide/), [배포](https://registry.npmjs.org/vite/latest), Node20.19+ 또는22.12+ |
| TypeScript | 7.0.2 후보 | [배포](https://registry.npmjs.org/typescript/latest), Vite/린트/타입 생성 호환은 P02에서 확인 |
| React Router | 8.3.1 후보 | [배포](https://registry.npmjs.org/react-router/latest), Node>=22.22 필요. 현재 Node22.13.1 불충족 |
| TanStack Query | 5.102.8 후보 | [배포](https://registry.npmjs.org/@tanstack/react-query/latest), React 호환 실제 테스트는 P02 |
| Node | 지원 LTS, 최소22.22 | 최신 LTS 패치 재조회·고정은 P01. 현재 하네스는 Python으로 실행되므로 앱 요구 불충족과 분리 |
| PostgreSQL/PostGIS | postgis/postgis:18-3.6 + digest | [공식 이미지](https://github.com/postgis/docker-postgis). manifest AMD64만 확인. Apple Silicon은 명시 에뮬레이션. 내부 실제 패치는 infra-check 기록 |
| Redis | 8.10.1-alpine + digest | [공식 릴리스](https://github.com/redis/redis/releases/latest), manifest ARM64/AMD64 확인. 실제 실행은 검증 기록 참조 |

PostGIS digest: `sha256:60f6ad1d21ea86a67d47780b9a0d1e1d200500f62b19293fa834d0dea80b8677`. container 태그를 latest로 바꾸지 않는다. PostgreSQL18 볼륨 경로는 `/var/lib/postgresql`이다.

Spring의 Security/JPA/Flyway/Validation/JDBC/Testcontainers 관리 범위는 실제 BOM을 확인하고 불필요하게 버전을 덮어쓰지 않는다. 프론트 패키지는 후보 숫자를 복사해 설치 완료로 취급하지 않는다. 하네스 단계에 의미 없는 앱 package/Gradle 파일은 만들지 않는다. 선택 변경 시 보안 패치·호환성 근거·확인일과 실제 build 결과를 함께 갱신한다.
