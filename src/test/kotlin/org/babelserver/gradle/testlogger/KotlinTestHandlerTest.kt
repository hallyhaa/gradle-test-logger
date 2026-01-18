package org.babelserver.gradle.testlogger

import org.gradle.api.logging.Logger
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class KotlinTestHandlerTest {

    private lateinit var tempDir: File
    private lateinit var mockLogger: MockLogger
    private lateinit var aggregator: TestAggregator

    @BeforeTest
    fun setUp() {
        tempDir = createTempDirectory("testlogger-test").toFile()
        mockLogger = MockLogger()
        aggregator = TestAggregator()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun parseTestResultsHandlesMissingDirectory() {
        val handler = KotlinTestHandler("test", mockLogger, aggregator, tempDir)

        // Call taskFinished which internally parses results
        handler.taskFinished()

        // Should not crash, aggregator should have 0 results
        val totalTestsField = TestAggregator::class.java.getDeclaredField("totalTests")
        totalTestsField.isAccessible = true
        assertEquals(0, totalTestsField.getInt(aggregator))
    }

    @Test
    fun parseTestResultsReadsValidXml() {
        val resultsDir = File(tempDir, "test-results/test")
        resultsDir.mkdirs()

        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="com.example.MyTest" tests="3" skipped="1" failures="1" errors="0" time="0.123">
              <testcase name="testSuccess" classname="com.example.MyTest" time="0.05"/>
              <testcase name="testFailed" classname="com.example.MyTest" time="0.03">
                <failure message="Expected 1 but was 2"/>
              </testcase>
              <testcase name="testSkipped" classname="com.example.MyTest" time="0.0">
                <skipped/>
              </testcase>
            </testsuite>
        """.trimIndent()

        File(resultsDir, "TEST-com.example.MyTest.xml").writeText(xml)

        val handler = KotlinTestHandler("test", mockLogger, aggregator, tempDir)
        handler.taskFinished()

        val totalTestsField = TestAggregator::class.java.getDeclaredField("totalTests")
        totalTestsField.isAccessible = true
        assertEquals(3, totalTestsField.getInt(aggregator))

        val passedField = TestAggregator::class.java.getDeclaredField("passedTests")
        passedField.isAccessible = true
        assertEquals(1, passedField.getInt(aggregator))

        val failedField = TestAggregator::class.java.getDeclaredField("failedTests")
        failedField.isAccessible = true
        assertEquals(1, failedField.getInt(aggregator))

        val skippedField = TestAggregator::class.java.getDeclaredField("skippedTests")
        skippedField.isAccessible = true
        assertEquals(1, skippedField.getInt(aggregator))
    }

    @Test
    fun parseTestResultsHandlesMultipleXmlFiles() {
        val resultsDir = File(tempDir, "test-results/test")
        resultsDir.mkdirs()

        val xml1 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="com.example.Test1" tests="2" skipped="0" failures="0" errors="0" time="0.1">
              <testcase name="test1" classname="com.example.Test1" time="0.05"/>
              <testcase name="test2" classname="com.example.Test1" time="0.05"/>
            </testsuite>
        """.trimIndent()

        val xml2 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="com.example.Test2" tests="3" skipped="0" failures="0" errors="0" time="0.2">
              <testcase name="test1" classname="com.example.Test2" time="0.05"/>
              <testcase name="test2" classname="com.example.Test2" time="0.05"/>
              <testcase name="test3" classname="com.example.Test2" time="0.1"/>
            </testsuite>
        """.trimIndent()

        File(resultsDir, "TEST-com.example.Test1.xml").writeText(xml1)
        File(resultsDir, "TEST-com.example.Test2.xml").writeText(xml2)

        val handler = KotlinTestHandler("test", mockLogger, aggregator, tempDir)
        handler.taskFinished()

        val totalTestsField = TestAggregator::class.java.getDeclaredField("totalTests")
        totalTestsField.isAccessible = true
        assertEquals(5, totalTestsField.getInt(aggregator))
    }

    @Test
    fun parseTestResultsHandlesErrorsAsFailures() {
        val resultsDir = File(tempDir, "test-results/test")
        resultsDir.mkdirs()

        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="com.example.MyTest" tests="2" skipped="0" failures="0" errors="1" time="0.1">
              <testcase name="testSuccess" classname="com.example.MyTest" time="0.05"/>
              <testcase name="testError" classname="com.example.MyTest" time="0.05">
                <error message="NullPointerException"/>
              </testcase>
            </testsuite>
        """.trimIndent()

        File(resultsDir, "TEST-com.example.MyTest.xml").writeText(xml)

        val handler = KotlinTestHandler("test", mockLogger, aggregator, tempDir)
        handler.taskFinished()

        val failedField = TestAggregator::class.java.getDeclaredField("failedTests")
        failedField.isAccessible = true
        assertEquals(1, failedField.getInt(aggregator))
    }

    @Test
    fun parseTestResultsSkipsMalformedXml() {
        val resultsDir = File(tempDir, "test-results/test")
        resultsDir.mkdirs()

        File(resultsDir, "TEST-malformed.xml").writeText("not valid xml <<<<")

        val validXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="com.example.Valid" tests="1" skipped="0" failures="0" errors="0" time="0.1">
              <testcase name="test1" classname="com.example.Valid" time="0.05"/>
            </testsuite>
        """.trimIndent()
        File(resultsDir, "TEST-valid.xml").writeText(validXml)

        val handler = KotlinTestHandler("test", mockLogger, aggregator, tempDir)
        handler.taskFinished()

        // Should still count the valid file
        val totalTestsField = TestAggregator::class.java.getDeclaredField("totalTests")
        totalTestsField.isAccessible = true
        assertEquals(1, totalTestsField.getInt(aggregator))
    }

    @Test
    fun parseTestResultsCleansKotlinJsTestNames() {
        val resultsDir = File(tempDir, "test-results/jsBrowserTest")
        resultsDir.mkdirs()

        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="com.example.JsTest" tests="1" skipped="0" failures="0" errors="0" time="0.01">
              <testcase name="myTest[js, browser, ChromeHeadless144.0.0.0, Linux0.0.0]" classname="com.example.JsTest" time="0.01"/>
            </testsuite>
        """.trimIndent()

        File(resultsDir, "TEST-com.example.JsTest.xml").writeText(xml)

        val handler = KotlinTestHandler("jsBrowserTest", mockLogger, aggregator, tempDir)
        handler.taskFinished()

        // Check that the log contains the cleaned test name with platform
        val logOutput = mockLogger.messages.joinToString("\n")
        assertTrue(logOutput.contains("myTest [browser]"), "Should contain cleaned test name 'myTest [browser]'")
        assertFalse(logOutput.contains("ChromeHeadless"), "Should not contain browser version")
    }

    // Simple mock logger for testing
    class MockLogger : Logger {
        val messages = mutableListOf<String>()

        override fun lifecycle(message: String) { messages.add(message) }
        override fun lifecycle(message: String, vararg objects: Any?) { messages.add(message) }
        override fun lifecycle(message: String, throwable: Throwable?) { messages.add(message) }

        // Required interface methods - not used in tests
        override fun getName() = "MockLogger"
        override fun isTraceEnabled() = false
        override fun isTraceEnabled(marker: org.slf4j.Marker?) = false
        override fun trace(message: String) {}
        override fun trace(format: String, arg: Any?) {}
        override fun trace(format: String, arg1: Any?, arg2: Any?) {}
        override fun trace(message: String, vararg objects: Any?) {}
        override fun trace(message: String, throwable: Throwable?) {}
        override fun trace(marker: org.slf4j.Marker?, msg: String?) {}
        override fun trace(marker: org.slf4j.Marker?, format: String?, arg: Any?) {}
        override fun trace(marker: org.slf4j.Marker?, format: String?, arg1: Any?, arg2: Any?) {}
        override fun trace(marker: org.slf4j.Marker?, format: String?, vararg argArray: Any?) {}
        override fun trace(marker: org.slf4j.Marker?, msg: String?, t: Throwable?) {}
        override fun isDebugEnabled() = false
        override fun isDebugEnabled(marker: org.slf4j.Marker?) = false
        override fun debug(message: String) {}
        override fun debug(format: String, arg: Any?) {}
        override fun debug(format: String, arg1: Any?, arg2: Any?) {}
        override fun debug(message: String, vararg objects: Any?) {}
        override fun debug(message: String, throwable: Throwable?) {}
        override fun debug(marker: org.slf4j.Marker?, msg: String?) {}
        override fun debug(marker: org.slf4j.Marker?, format: String?, arg: Any?) {}
        override fun debug(marker: org.slf4j.Marker?, format: String?, arg1: Any?, arg2: Any?) {}
        override fun debug(marker: org.slf4j.Marker?, format: String?, vararg argArray: Any?) {}
        override fun debug(marker: org.slf4j.Marker?, msg: String?, t: Throwable?) {}
        override fun isInfoEnabled() = false
        override fun isInfoEnabled(marker: org.slf4j.Marker?) = false
        override fun info(message: String) {}
        override fun info(format: String, arg: Any?) {}
        override fun info(format: String, arg1: Any?, arg2: Any?) {}
        override fun info(message: String, vararg objects: Any?) {}
        override fun info(message: String, throwable: Throwable?) {}
        override fun info(marker: org.slf4j.Marker?, msg: String?) {}
        override fun info(marker: org.slf4j.Marker?, format: String?, arg: Any?) {}
        override fun info(marker: org.slf4j.Marker?, format: String?, arg1: Any?, arg2: Any?) {}
        override fun info(marker: org.slf4j.Marker?, format: String?, vararg argArray: Any?) {}
        override fun info(marker: org.slf4j.Marker?, msg: String?, t: Throwable?) {}
        override fun isWarnEnabled() = false
        override fun isWarnEnabled(marker: org.slf4j.Marker?) = false
        override fun warn(message: String) {}
        override fun warn(format: String, arg: Any?) {}
        override fun warn(format: String, arg1: Any?, arg2: Any?) {}
        override fun warn(message: String, vararg objects: Any?) {}
        override fun warn(message: String, throwable: Throwable?) {}
        override fun warn(marker: org.slf4j.Marker?, msg: String?) {}
        override fun warn(marker: org.slf4j.Marker?, format: String?, arg: Any?) {}
        override fun warn(marker: org.slf4j.Marker?, format: String?, arg1: Any?, arg2: Any?) {}
        override fun warn(marker: org.slf4j.Marker?, format: String?, vararg argArray: Any?) {}
        override fun warn(marker: org.slf4j.Marker?, msg: String?, t: Throwable?) {}
        override fun isErrorEnabled() = false
        override fun isErrorEnabled(marker: org.slf4j.Marker?) = false
        override fun error(message: String) {}
        override fun error(format: String, arg: Any?) {}
        override fun error(format: String, arg1: Any?, arg2: Any?) {}
        override fun error(message: String, vararg objects: Any?) {}
        override fun error(message: String, throwable: Throwable?) {}
        override fun error(marker: org.slf4j.Marker?, msg: String?) {}
        override fun error(marker: org.slf4j.Marker?, format: String?, arg: Any?) {}
        override fun error(marker: org.slf4j.Marker?, format: String?, arg1: Any?, arg2: Any?) {}
        override fun error(marker: org.slf4j.Marker?, format: String?, vararg argArray: Any?) {}
        override fun error(marker: org.slf4j.Marker?, msg: String?, t: Throwable?) {}
        override fun isLifecycleEnabled() = true
        override fun isQuietEnabled() = true
        override fun quiet(message: String) {}
        override fun quiet(message: String, vararg objects: Any?) {}
        override fun quiet(message: String, throwable: Throwable?) {}
        override fun isEnabled(level: org.gradle.api.logging.LogLevel) = true
        override fun log(level: org.gradle.api.logging.LogLevel, message: String) {}
        override fun log(level: org.gradle.api.logging.LogLevel, message: String, vararg objects: Any?) {}
        override fun log(level: org.gradle.api.logging.LogLevel, message: String, throwable: Throwable?) {}
    }
}
