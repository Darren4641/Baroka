import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.1.0"
    id("io.spring.dependency-management") version "1.1.5"
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
//    kotlin("jvm") version "1.8.22"
//    kotlin("plugin.spring") version "1.8.22"
//    kotlin("plugin.jpa") version "1.8.22"
}

group = "io.baroka"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // spring web
    implementation("org.springframework.boot:spring-boot-starter-web")
    // spring websocket
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    // kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // annotation processor
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // gson
    implementation("com.google.code.gson:gson:2.10.1")

    // JSch
    implementation("com.github.mwiede:jsch:0.2.3")

    //jackson-module-kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")


    //thymeleaf
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.thymeleaf:thymeleaf:3.1.0.RELEASE")
    implementation("org.thymeleaf:thymeleaf-spring5:3.1.0.RELEASE")
    implementation("nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect:3.1.0")
    implementation(kotlin("stdlib-jdk8"))

}


kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
    jvmToolchain(17)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
