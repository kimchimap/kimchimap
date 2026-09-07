plugins {
  java
  id("org.springframework.boot") version "4.1.1"
  id("com.diffplug.spotless") version "8.10.2"
}

group = "kr.kimchimap"

version = "0.1.0"

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

repositories { mavenCentral() }

dependencies {
  implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
  implementation("org.springframework.boot:spring-boot-starter-webmvc")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-data-redis")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.1.1")
  runtimeOnly("org.postgresql:postgresql")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
  testImplementation("org.springframework.boot:spring-boot-starter-security-test")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.testcontainers:testcontainers-postgresql")
  testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
  systemProperty("user.timezone", "UTC")
  testLogging { events("failed", "skipped") }
}

tasks.test { useJUnitPlatform { excludeTags("integration") } }

tasks.register<Test>("integrationTest") {
  description = "실제 PostGIS와 Redis 통합 검증"
  group = "verification"
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  useJUnitPlatform {
    includeTags("integration")
    excludeTags("performance")
  }
  shouldRunAfter(tasks.test)
}

tasks.register<Test>("performanceTest") {
  description = "실제 검색 쿼리 실행 계획과 측정 결과"
  group = "verification"
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  useJUnitPlatform { includeTags("performance") }
  outputs.upToDateWhen { false }
}

tasks.register<Test>("generateOpenApi") {
  description = "실제 서버 계약을 결정적으로 생성"
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  useJUnitPlatform {
    includeTags("integration")
    excludeTags("performance")
  }
  filter { includeTestsMatching("*FoundationIntegrationTest.exportsOpenApi") }
  outputs.upToDateWhen { false }
}

spotless {
  java {
    googleJavaFormat("1.36.1")
    removeUnusedImports()
    trimTrailingWhitespace()
    endWithNewline()
  }
  kotlinGradle {
    ktfmt("0.64")
    trimTrailingWhitespace()
    endWithNewline()
  }
}
