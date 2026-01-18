plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    id("org.owasp.dependencycheck") version "12.1.0"
}

group = "org.babelserver.gradle"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        create("testLogger") {
            id = "org.babelserver.gradle.test-logger"
            implementationClass = "org.babelserver.gradle.testlogger.TestLoggerPlugin"
            displayName = "Test Logger Plugin"
            description = "Provides pretty test output with status indicators for each test"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
