plugins {
	java
	id("org.springframework.boot") version "3.5.6"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "ai.email"
version = "0.0.1-SNAPSHOT"
description = "a service that uses local llms to respond to emails"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

extra["springAiVersion"] = "1.0.2"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.integration:spring-integration-mail")
	implementation("org.springframework.integration:spring-integration-core")
	implementation("jakarta.mail:jakarta.mail-api")
	implementation("org.eclipse.angus:angus-mail:2.0.3") // Jakarta Mail implementation

	// OAuth2 dependencies
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("com.microsoft.graph:microsoft-graph:6.15.0")
	implementation("com.azure:azure-identity:1.13.2")
	implementation("com.google.api-client:google-api-client:2.7.0")
	implementation("com.google.oauth-client:google-oauth-client-jetty:1.36.0")
	implementation("com.google.apis:google-api-services-gmail:v1-rev20240520-2.0.0")

	runtimeOnly("com.h2database:h2")
	runtimeOnly("org.postgresql:postgresql")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	developmentOnly("org.springframework.ai:spring-ai-spring-boot-docker-compose")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
