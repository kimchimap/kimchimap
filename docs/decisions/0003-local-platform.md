# ADR 0003: 로컬 PostGIS 아키텍처

PostGIS 자체는 ARM64에서 사용할 수 있다. 여기서 AMD64 한정은 선택한 `postgis/postgis:18-3.6` 이미지의 manifest에 대한 설명이다. PostGIS 소프트웨어 전체의 아키텍처 제한이 아니다.

상태: 채택, 2026-09-07.
공식 postgis/docker-postgis 문서와 실제 manifest가 AMD64만 제공한다. 로컬 compose에 linux/amd64를 명시한다. Apple Silicon은 Docker Desktop 에뮬레이션을 사용하고 네이티브 ARM64 지원으로 보고하지 않는다. Redis manifest는 ARM64/AMD64를 제공한다.

자체 PostGIS 이미지를 확인 없이 빌드하거나 비공식 이미지를 도입하는 대신 검증된 upstream digest를 고정한다. Linux ARM64에서도 에뮬레이션 준비가 필요하며 기본 네이티브 실행을 보장하지 않는다. 성능 결과에 에뮬레이션 여부를 기록한다. 네이티브 ARM64가 필수면 후속 단계에서 공급망·라이선스·빌드 재현성을 검증한 이미지 ADR로 교체한다.

ARM64 패키지 존재는 [Debian 공식 패키지 목록](https://packages.debian.org/trixie/postgis)에서도 확인했다. 이는 선택한 Docker 태그에 ARM64 manifest가 있다는 뜻은 아니다.
