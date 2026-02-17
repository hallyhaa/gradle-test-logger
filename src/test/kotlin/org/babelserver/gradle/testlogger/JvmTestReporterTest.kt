package org.babelserver.gradle.testlogger

import org.gradle.api.logging.Logger
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import kotlin.test.*

class JvmTestReporterTest {

    private lateinit var mockLogger: MockLogger
    private lateinit var aggregator: TestAggregator
    private lateinit var listener: JvmTestReporter

    @BeforeTest
    fun setUp() {
        mockLogger = MockLogger()
        aggregator = TestAggregator()
        listener = JvmTestReporter("test", mockLogger, aggregator)
    }

    @Test
    fun afterTestCountsSuccessfulTests() {
        listener.taskStarted()

        listener.afterTest(mockDescriptor("test1"), mockResult(TestResult.ResultType.SUCCESS))
        listener.afterTest(mockDescriptor("test2"), mockResult(TestResult.ResultType.SUCCESS))

        listener.taskFinished()

        val passedField = TestAggregator::class.java.getDeclaredField("passedTests")
        passedField.isAccessible = true
        assertEquals(2, passedField.getInt(aggregator))
    }

    @Test
    fun afterTestCountsFailedTests() {
        listener.taskStarted()

        listener.afterTest(mockDescriptor("test1"), mockResult(TestResult.ResultType.FAILURE))

        listener.taskFinished()

        val failedField = TestAggregator::class.java.getDeclaredField("failedTests")
        failedField.isAccessible = true
        assertEquals(1, failedField.getInt(aggregator))
    }

    @Test
    fun afterTestCountsSkippedTests() {
        listener.taskStarted()

        listener.afterTest(mockDescriptor("test1"), mockResult(TestResult.ResultType.SKIPPED))

        listener.taskFinished()

        val skippedField = TestAggregator::class.java.getDeclaredField("skippedTests")
        skippedField.isAccessible = true
        assertEquals(1, skippedField.getInt(aggregator))
    }

    @Test
    fun afterTestCountsMixedResults() {
        listener.taskStarted()

        listener.afterTest(mockDescriptor("pass1"), mockResult(TestResult.ResultType.SUCCESS))
        listener.afterTest(mockDescriptor("pass2"), mockResult(TestResult.ResultType.SUCCESS))
        listener.afterTest(mockDescriptor("fail1"), mockResult(TestResult.ResultType.FAILURE))
        listener.afterTest(mockDescriptor("skip1"), mockResult(TestResult.ResultType.SKIPPED))

        listener.taskFinished()

        val totalField = TestAggregator::class.java.getDeclaredField("totalTests")
        totalField.isAccessible = true
        assertEquals(4, totalField.getInt(aggregator))
    }

    @Test
    fun taskFinishedDoesNothingIfTaskWasNotRun() {
        // Don't call taskStarted()
        listener.taskFinished()

        val tasksRunField = TestAggregator::class.java.getDeclaredField("tasksRun")
        tasksRunField.isAccessible = true
        assertEquals(0, tasksRunField.getInt(aggregator))
    }

    @Test
    fun taskStartedLogsHeader() {
        listener.taskStarted()

        val logOutput = mockLogger.messages.joinToString("\n")
        assertTrue(logOutput.contains("T E S T S"))
        assertTrue(logOutput.contains("test"))
    }

    @Test
    fun taskFinishedLogsSummary() {
        listener.taskStarted()
        listener.afterTest(mockDescriptor("test1"), mockResult(TestResult.ResultType.SUCCESS))
        listener.taskFinished()

        val logOutput = mockLogger.messages.joinToString("\n")
        assertTrue(logOutput.contains("Tests:"))
        assertTrue(logOutput.contains("Passed:"))
    }

    @Test
    fun outputIncludesDurationInMilliseconds() {
        listener.taskStarted()
        listener.afterTest(mockDescriptor("fastTest"), mockResult(TestResult.ResultType.SUCCESS, startTime = 0, endTime = 42))
        listener.afterSuite(mockSuiteDescriptor(), mockSuiteResult(1, 0, 0, startTime = 0, endTime = 42))
        listener.taskFinished()

        val logOutput = mockLogger.messages.joinToString("\n")
        assertTrue(logOutput.contains("(42ms)"), "Should show duration in milliseconds\nOutput:\n$logOutput")
    }

    @Test
    fun outputIncludesDurationInSeconds() {
        listener.taskStarted()
        listener.afterTest(mockDescriptor("slowTest"), mockResult(TestResult.ResultType.SUCCESS, startTime = 0, endTime = 1500))
        listener.afterSuite(mockSuiteDescriptor(), mockSuiteResult(1, 0, 0, startTime = 0, endTime = 1500))
        listener.taskFinished()

        val logOutput = mockLogger.messages.joinToString("\n")
        val expected = String.format("%.1f", 1.5) // Locale-aware: "1.5" or "1,5"
        assertTrue(logOutput.contains("(${expected}s)"), "Should show duration in seconds\nOutput:\n$logOutput")
    }

    @Test
    fun failureOutputIncludesErrorMessage() {
        listener.taskStarted()
        val exception = AssertionError("expected <200> but was <404>")
        listener.afterTest(mockDescriptor("failingTest"), mockResult(
            TestResult.ResultType.FAILURE, exceptions = listOf(exception)
        ))
        listener.afterSuite(mockSuiteDescriptor(), mockSuiteResult(1, 1, 0))
        listener.taskFinished()

        val logOutput = mockLogger.messages.joinToString("\n")
        assertTrue(logOutput.contains("expected <200> but was <404>"),
            "Should show error message\nOutput:\n$logOutput")
        assertTrue(logOutput.contains("AssertionError"),
            "Should show exception type\nOutput:\n$logOutput")
    }

    @Test
    fun failureOutputIncludesCauseMessage() {
        listener.taskStarted()
        val cause = IllegalStateException("connection refused")
        val exception = RuntimeException("request failed", cause)
        listener.afterTest(mockDescriptor("failingTest"), mockResult(
            TestResult.ResultType.FAILURE, exceptions = listOf(exception)
        ))
        listener.afterSuite(mockSuiteDescriptor(), mockSuiteResult(1, 1, 0))
        listener.taskFinished()

        val logOutput = mockLogger.messages.joinToString("\n")
        assertTrue(logOutput.contains("request failed"),
            "Should show exception message\nOutput:\n$logOutput")
        assertTrue(logOutput.contains("Caused by"),
            "Should show cause chain\nOutput:\n$logOutput")
        assertTrue(logOutput.contains("connection refused"),
            "Should show cause message\nOutput:\n$logOutput")
    }

    @Test
    fun outputIncludesClassHeaderOnFlush() {
        listener.taskStarted()
        listener.afterTest(mockDescriptor("myTest"), mockResult(TestResult.ResultType.SUCCESS))
        listener.afterSuite(mockSuiteDescriptor(), mockSuiteResult(1, 0, 0))
        listener.taskFinished()

        val logOutput = mockLogger.messages.joinToString("\n")
        assertTrue(logOutput.contains("Running com.example.TestClass"), "Should show class header\nOutput:\n$logOutput")
        assertTrue(logOutput.contains("myTest"), "Should show test name\nOutput:\n$logOutput")
    }

    // Mock implementations

    private fun mockDescriptor(name: String): TestDescriptor {
        return object : TestDescriptor {
            override fun getName() = name
            override fun getDisplayName() = name
            override fun getClassName() = "com.example.TestClass"
            override fun getParent(): TestDescriptor? = null
            override fun isComposite() = false
        }
    }

    private fun mockSuiteDescriptor(className: String = "com.example.TestClass"): TestDescriptor {
        val parentDesc = object : TestDescriptor {
            override fun getName() = "Gradle Test"
            override fun getDisplayName() = "Gradle Test"
            override fun getClassName(): String? = null
            override fun getParent(): TestDescriptor? = null
            override fun isComposite() = true
        }
        return object : TestDescriptor {
            override fun getName() = className
            override fun getDisplayName() = className
            override fun getClassName() = className
            override fun getParent() = parentDesc
            override fun isComposite() = true
        }
    }

    private fun mockResult(
        resultType: TestResult.ResultType,
        startTime: Long = 0, endTime: Long = 100,
        exceptions: List<Throwable> = emptyList()
    ): TestResult {
        return object : TestResult {
            override fun getResultType() = resultType
            override fun getStartTime() = startTime
            override fun getEndTime() = endTime
            override fun getTestCount() = 1L
            override fun getSuccessfulTestCount() = if (resultType == TestResult.ResultType.SUCCESS) 1L else 0L
            override fun getFailedTestCount() = if (resultType == TestResult.ResultType.FAILURE) 1L else 0L
            override fun getSkippedTestCount() = if (resultType == TestResult.ResultType.SKIPPED) 1L else 0L
            override fun getException() = exceptions.firstOrNull()
            override fun getExceptions() = exceptions
            override fun getFailures() = emptyList<org.gradle.api.tasks.testing.TestFailure>()
            override fun getAssumptionFailure(): org.gradle.api.tasks.testing.TestFailure? = null
        }
    }

    private fun mockSuiteResult(
        total: Long = 1, failed: Long = 0, skipped: Long = 0,
        startTime: Long = 0, endTime: Long = 100
    ): TestResult {
        return object : TestResult {
            override fun getResultType() = when {
                failed > 0 -> TestResult.ResultType.FAILURE
                skipped == total -> TestResult.ResultType.SKIPPED
                else -> TestResult.ResultType.SUCCESS
            }
            override fun getStartTime() = startTime
            override fun getEndTime() = endTime
            override fun getTestCount() = total
            override fun getSuccessfulTestCount() = total - failed - skipped
            override fun getFailedTestCount() = failed
            override fun getSkippedTestCount() = skipped
            override fun getException() = null
            override fun getExceptions() = emptyList<Throwable>()
            override fun getFailures() = emptyList<org.gradle.api.tasks.testing.TestFailure>()
            override fun getAssumptionFailure(): org.gradle.api.tasks.testing.TestFailure? = null
        }
    }

    // Simple mock logger
    class MockLogger : Logger {
        val messages = mutableListOf<String>()

        override fun lifecycle(message: String) { messages.add(message) }
        override fun lifecycle(message: String, vararg objects: Any?) { messages.add(message) }
        override fun lifecycle(message: String, throwable: Throwable?) { messages.add(message) }

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
