plugins {
    java
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "dev.discord"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// Enable Java 25 preview features
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--enable-preview"))
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
    useJUnitPlatform()
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview")
}

dependencies {
    // WebFlux (Netty HTTP server + ReactiveWebSocketClient)
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Reactive Redis (Lettuce driver)
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // Actuator (health, metrics)
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}
