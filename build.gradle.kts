import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
	id("org.springframework.boot") version "3.3.2"
	id("io.spring.dependency-management") version "1.1.6"
	kotlin("jvm") version "1.9.24"
	kotlin("plugin.spring") version "1.9.24"
}

group = "309technology.vcm.omar"
version = "0.0.1-SNAPSHOT"
description = "secure media vault for files"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories { mavenCentral() }


dependencies {
	// Spring reactive stack
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-amqp")

	// MinIO SDK
	implementation("io.minio:minio:8.5.11")

	// Kotlin + coroutines
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.8.1"){
		version { strictly("1.8.1") }
	}

	// Reactor Kotlin extensions (para 'mono { }', 'flux { }', etc.)
	implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.2")

	// Testing
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("io.projectreactor:reactor-test")
}


tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "17"
	}
}


tasks.withType<Test> { useJUnitPlatform() }