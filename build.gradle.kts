plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("org.babelserver.gradle.test-logger") version "1.0.1"
    id("com.gradle.plugin-publish") version "2.0.0" // https://plugins.gradle.org/plugin/com.gradle.plugin-publish
}

group = "org.babelserver.gradle"
version = "1.1.0"

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

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile> {
    options.release.set(17)
}

tasks.test {
    useJUnitPlatform()
}
