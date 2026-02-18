package org.babelserver.gradle.testlogger

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtensionFunctionalTest {

    private lateinit var projectDir: File

    @BeforeTest
    fun setUp() {
        projectDir = createTempDirectory("extension-test").toFile()

        File(projectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "extension-test"
        """.trimIndent())

        val testDir = File(projectDir, "src/test/java/com/example")
        testDir.mkdirs()
        File(testDir, "SampleTest.java").writeText("""
            package com.example;

            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.*;

            class SampleTest {
                @Test void passing() { assertTrue(true); }
                @Test void another() { assertTrue(true); }
            }
        """.trimIndent())
    }

    @AfterTest
    fun tearDown() {
        projectDir.deleteRecursively()
    }

    @Test
    fun showIndividualResultsFalseViaDsl() {
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

            testLogger {
                showIndividualResults = false
            }

            tasks.test {
                useJUnitPlatform()
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("test")
            .forwardOutput()
            .build()

        val output = result.output
        assertTrue(output.contains("Babelserver test-logger"), "Should show test header\nOutput:\n$output")
        assertFalse(output.contains("Running com.example.SampleTest"),
            "Should NOT show class header\nOutput:\n$output")
        assertFalse(output.contains("Tests run:"),
            "Should NOT show per-class summary\nOutput:\n$output")
        assertFalse(output.contains("passing"),
            "Should NOT show individual test names\nOutput:\n$output")
        assertFalse(output.contains("another"),
            "Should NOT show individual test names\nOutput:\n$output")
    }

    @Test
    fun showIndividualResultsFalseViaCliProperty() {
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
            .withArguments("test", "-Ptestlogger.showIndividualResults=false")
            .forwardOutput()
            .build()

        val output = result.output
        assertFalse(output.contains("Running com.example.SampleTest"),
            "Should NOT show class header with CLI override\nOutput:\n$output")
        assertFalse(output.contains("passing"),
            "Should NOT show individual test names with CLI override\nOutput:\n$output")
    }

    @Test
    fun cliPropertyOverridesDslSetting() {
        // DSL says show (default true), CLI says hide
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

            testLogger {
                showIndividualResults = true
            }

            tasks.test {
                useJUnitPlatform()
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("test", "-Ptestlogger.showIndividualResults=false")
            .forwardOutput()
            .build()

        val output = result.output
        assertFalse(output.contains("Running com.example.SampleTest"),
            "CLI override should suppress class header\nOutput:\n$output")
        assertFalse(output.contains("passing"),
            "CLI override should win over DSL setting\nOutput:\n$output")
    }

    @Test
    fun defaultBehaviorShowsIndividualResults() {
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
            .withArguments("test")
            .forwardOutput()
            .build()

        val output = result.output
        assertTrue(output.contains("passing"),
            "Default should show individual test names\nOutput:\n$output")
        assertTrue(output.contains("another"),
            "Default should show individual test names\nOutput:\n$output")
    }
}
