plugins {
    java
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.10.6"
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

repositories {
    mavenCentral()
}

dependencies {
    // Web (Tomcat + Spring MVC)
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Redis (Lettuce driver)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
