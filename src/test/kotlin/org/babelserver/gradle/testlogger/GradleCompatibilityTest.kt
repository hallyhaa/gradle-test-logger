package org.babelserver.gradle.testlogger

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies that the plugin works with older Gradle versions.
 * Gradle 8.5 is the oldest version that supports Java 21 as a runtime.
 */
class GradleCompatibilityTest {

    private lateinit var projectDir: File

    @BeforeTest
    fun setUp() {
        projectDir = createTempDirectory("gradle-compat-test").toFile()

        File(projectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "compat-test"
        """.trimIndent())

        val testDir = File(projectDir, "src/test/java/com/example")
        testDir.mkdirs()
        File(testDir, "SampleTest.java").writeText("""
            package com.example;

            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.*;

            class SampleTest {
                @Test void passing() { assertTrue(true); }
                @Test void failing() { fail("expected failure"); }
            }
        """.trimIndent())
    }

    @AfterTest
    fun tearDown() {
        projectDir.deleteRecursively()
    }

    @Test
    fun pluginWorksWithGradle85() {
        runWithGradleVersion("8.5")
    }

    @Test
    fun pluginWorksWithGradle812() {
        runWithGradleVersion("8.12")
    }

    @Test
    fun pluginWorksWithConfigurationCache() {
        // Use only passing tests for this test
        val testDir = File(projectDir, "src/test/java/com/example")
        File(testDir, "SampleTest.java").writeText("""
            package com.example;

            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.*;

            class SampleTest {
                @Test void passing() { assertTrue(true); }
                @Test void alsoPass() { assertEquals(1, 1); }
            }
        """.trimIndent())

        File(projectDir, "build.gradle.kts").writeText("""
            plugins {
                java
                id("org.babelserver.gradle.test-logger")
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
                testRuntimeOnly("org.junit.platform:junit-platform-launcher")
            }

            tasks.test {
                useJUnitPlatform()
            }
        """.trimIndent())

        // First run: store configuration cache
        val result1 = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("test", "--configuration-cache")
            .forwardOutput()
            .build()

        assertTrue(result1.output.contains("Babelserver test-logger"), "First run should produce test output")
        assertTrue(result1.output.contains("Configuration cache entry stored"),
            "Should store configuration cache entry")

        // Second run: reuse configuration cache (same arguments to hit the cache)
        // Tests will be UP-TO-DATE, but the configuration cache should be reused without errors
        val result2 = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("test", "--configuration-cache")
            .forwardOutput()
            .build()

        assertTrue(result2.output.contains("Reusing configuration cache"),
            "Should reuse configuration cache entry\nOutput:\n${result2.output}")
    }

    private fun runWithGradleVersion(gradleVersion: String) {
        File(projectDir, "build.gradle.kts").writeText("""
            plugins {
                java
                id("org.babelserver.gradle.test-logger")
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
                testRuntimeOnly("org.junit.platform:junit-platform-launcher")
            }

            tasks.test {
                useJUnitPlatform()
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withGradleVersion(gradleVersion)
            .withArguments("test", "--info", "--stacktrace")
            .forwardOutput()
            .buildAndFail() // We expect failure because of the failing test

        val output = result.output
        assertTrue(output.contains("Babelserver test-logger"), "Should contain test header (Gradle $gradleVersion)\nOutput:\n$output")
        assertTrue(output.contains("passing"), "Should show passing test (Gradle $gradleVersion)")
        assertTrue(output.contains("failing"), "Should show failing test (Gradle $gradleVersion)")
    }
}
