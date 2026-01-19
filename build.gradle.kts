plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.0.0"
}

group = "org.babelserver.gradle"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

gradlePlugin {
    website = "https://github.com/hallyhaa/gradle-test-logger"
    vcsUrl = "https://github.com/hallyhaa/gradle-test-logger.git"

    plugins {
        create("testLogger") {
            id = "org.babelserver.gradle.test-logger"
            implementationClass = "org.babelserver.gradle.testlogger.TestLoggerPlugin"
            displayName = "Test Logger Plugin"
            description = "Provides pretty test output with status indicators for each test"
            tags = listOf(
                "testing", "test", "logging", "output", "reporting",
                "kotlin", "kotlin-multiplatform", "kotlin-js", "kotlin-native",
                "junit", "test-results"
            )
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
