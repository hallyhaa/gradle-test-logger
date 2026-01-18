package org.babelserver.gradle.testlogger

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class TestSuitesFunctionalTest {

    private lateinit var projectDir: File

    @BeforeTest
    fun setUp() {
        projectDir = createTempDirectory("test-suites-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        projectDir.deleteRecursively()
    }

    @Test
    fun pluginWorksWithTestSuites() {
        // Create build.gradle.kts with Test Suites
        File(projectDir, "build.gradle.kts").writeText("""
            plugins {
                java
                `jvm-test-suite`
                id("org.babelserver.gradle.test-logger")
            }

            repositories {
                mavenCentral()
            }

            testing {
                suites {
                    val test by getting(JvmTestSuite::class) {
                        useJUnitJupiter("5.10.0")
                    }

                    val integrationTest by registering(JvmTestSuite::class) {
                        useJUnitJupiter("5.10.0")

                        targets {
                            all {
                                testTask.configure {
                                    shouldRunAfter(test)
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent())

        File(projectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "test-suites-project"
        """.trimIndent())

        // Unit test
        val testDir = File(projectDir, "src/test/java/com/example")
        testDir.mkdirs()
        File(testDir, "UnitTest.java").writeText("""
            package com.example;

            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.*;

            class UnitTest {
                @Test
                void unitTestPasses() {
                    assertTrue(true);
                }
            }
        """.trimIndent())

        // Integration test
        val integrationTestDir = File(projectDir, "src/integrationTest/java/com/example")
        integrationTestDir.mkdirs()
        File(integrationTestDir, "IntegrationTest.java").writeText("""
            package com.example;

            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.*;

            class IntegrationTest {
                @Test
                void integrationTestPasses() {
                    assertTrue(true);
                }
            }
        """.trimIndent())

        // Both test suites
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("test", "integrationTest", "--info")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":integrationTest")?.outcome)

        val output = result.output
        assertTrue(output.contains("T E S T S"), "Should contain test header")
        assertTrue(output.contains("UnitTest") || output.contains("unitTestPasses"),
            "Should contain unit test output")
        assertTrue(output.contains("IntegrationTest") || output.contains("integrationTestPasses"),
            "Should contain integration test output")
    }

    @Test
    fun pluginShowsTotalForMultipleTestSuites() {
        // Create build.gradle.kts with multiple Test Suites
        File(projectDir, "build.gradle.kts").writeText("""
            plugins {
                java
                `jvm-test-suite`
                id("org.babelserver.gradle.test-logger")
            }

            repositories {
                mavenCentral()
            }

            testing {
                suites {
                    val test by getting(JvmTestSuite::class) {
                        useJUnitJupiter("5.10.0")
                    }

                    val integrationTest by registering(JvmTestSuite::class) {
                        useJUnitJupiter("5.10.0")
                    }
                }
            }
        """.trimIndent())

        File(projectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "multi-suite-project"
        """.trimIndent())

        // Create unit test
        val testDir = File(projectDir, "src/test/java/com/example")
        testDir.mkdirs()
        File(testDir, "UnitTest.java").writeText("""
            package com.example;

            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.*;

            class UnitTest {
                @Test void test1() { assertTrue(true); }
                @Test void test2() { assertTrue(true); }
            }
        """.trimIndent())

        // Integration test
        val integrationTestDir = File(projectDir, "src/integrationTest/java/com/example")
        integrationTestDir.mkdirs()
        File(integrationTestDir, "IntegrationTest.java").writeText("""
            package com.example;

            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.*;

            class IntegrationTest {
                @Test void test1() { assertTrue(true); }
                @Test void test2() { assertTrue(true); }
                @Test void test3() { assertTrue(true); }
            }
        """.trimIndent())

        // Both test suites
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("test", "integrationTest")
            .forwardOutput()
            .build()

        val output = result.output
        assertTrue(output.contains("TOTAL"), "Should show TOTAL summary for multiple test tasks")
        assertTrue(output.contains("2 test tasks"), "Should mention 2 test tasks")
    }
}
