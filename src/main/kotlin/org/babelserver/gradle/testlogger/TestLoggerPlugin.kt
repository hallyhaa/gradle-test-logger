package org.babelserver.gradle.testlogger

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.provider.SetProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import java.io.File
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory

// Shared constants for test output formatting
private object TestOutputStyle {

    // Fallback on Windows w/o modern terminal
    private val useAscii: Boolean by lazy {
        System.getProperty("os.name")?.lowercase()?.contains("windows") == true
                &&
        System.getenv("WT_SESSION") == null && System.getenv("TERM_PROGRAM") == null
    }

    val PASSED: String get() = if (useAscii) "[OK]" else "✅"
    val FAILED: String get() = if (useAscii) "[FAIL]" else "❌"
    val SKIPPED: String get() = if (useAscii) "[SKIP]" else "⏭️"
    val LINE_SINGLE: String get() = if (useAscii) "-" else "─"
    val LINE_DOUBLE: String get() = if (useAscii) "=" else "═"

    // ANSI codes - OK in cmd.exe since Win10, apparently
    const val ANSI_GREEN = "\u001B[32m"
    const val ANSI_RED = "\u001B[31m"
    const val ANSI_YELLOW = "\u001B[33m"
    const val ANSI_RESET = "\u001B[0m"
    const val CLEAR_TO_EOL = "\u001B[K"

    fun isEnabled(): Boolean {
        val env = System.getenv("TESTLOGGER_ENABLED")
        return env == null || env.lowercase() != "false"
    }
}

// BuildService that listens for task completion events
abstract class TestLoggerBuildService : BuildService<TestLoggerBuildService.Params>, OperationCompletionListener, AutoCloseable {

    interface Params : BuildServiceParameters {
        val testTaskPaths: SetProperty<String>
    }

    private val upToDateTasks = mutableSetOf<String>()

    override fun onFinish(event: FinishEvent) {
        if (event is TaskFinishEvent) {
            val taskPath = event.descriptor.taskPath
            val result = event.result
            // Check if this is a test task that was skipped (UP-TO-DATE)
            if (parameters.testTaskPaths.get().contains(taskPath) && result is TaskSkippedResult) {
                upToDateTasks.add(taskPath)
                // Trigger Gradle's progress line, but don't produce one ourselves
                println("\b")
            }
        }
    }

    override fun close() {
        // We implement AutoCloseable ...
    }
}

// BuildService that holds the aggregator and prints at the end via close()
abstract class TestAggregatorService : BuildService<TestAggregatorService.Params>, AutoCloseable {

    interface Params : BuildServiceParameters

    val aggregator = TestAggregator()
    private var logger: Logger? = null

    fun setLogger(logger: Logger) {
        this.logger = logger
    }

    fun printTotal() {
        logger?.let { aggregator.printTotalIfMultipleTasks(it) }
    }

    override fun close() {
        // Service is closing, print totals if we have a logger
        printTotal()
    }
}

abstract class TestLoggerPlugin : Plugin<Project> {

    companion object {
        // Kotlin test task class names we look for
        private val KOTLIN_TEST_TASK_CLASSES = setOf(
            "org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest",
            "org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest",
            "org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest"
        )

        private fun isKotlinTestTask(className: String): Boolean {
            return KOTLIN_TEST_TASK_CLASSES.any { className.startsWith(it) }
        }
    }

    @get:Inject
    abstract val eventsListenerRegistry: BuildEventsListenerRegistry

    override fun apply(project: Project) {
        // Check Gradle property
        val prop = project.findProperty("testlogger.enabled")?.toString()
        if (prop != null && prop.lowercase() == "false") {
            return
        }

        val gradle = project.gradle

        // Register aggregator service (shared across all projects in the build)
        val aggregatorServiceProvider = gradle.sharedServices.registerIfAbsent(
            "testLoggerAggregator",
            TestAggregatorService::class.java
        ) {}

        val aggregatorService = aggregatorServiceProvider.get()
        aggregatorService.setLogger(project.logger)
        val aggregator = aggregatorService.aggregator

        val testTaskPaths = mutableSetOf<String>()

        // Standard JVM Test tasks
        project.tasks.withType(Test::class.java).configureEach {
            val listener = JvmTestReporter(this.name, this.logger, aggregator)
            testTaskPaths.add(this.path)
            configureTestTask(this, listener)
        }

        // Kotlin test tasks (JS, Native, etc.)
        project.tasks.configureEach {
            val task = this
            val className = task.javaClass.name
            // Check if this is a Kotlin test task (but not already a standard Test task)
            if (task !is Test && isKotlinTestTask(className)) {
                configureKotlinTestTask(task, project, aggregator)
            }
        }

        // Register build service for task events (UP-TO-DATE detection)
        val listenerServiceProvider = gradle.sharedServices.registerIfAbsent(
            "testLoggerListener",
            TestLoggerBuildService::class.java
        ) {
            @Suppress("kotlin:S6518") // Assignment syntax not available in plugin code
            parameters.testTaskPaths.set(testTaskPaths)
        }

        eventsListenerRegistry.onTaskCompletion(listenerServiceProvider)
    }

    private fun configureTestTask(testTask: Test, listener: JvmTestReporter) {
        testTask.addTestListener(listener)

        // Suppress Gradle's default test failure output — the plugin handles it
        testTask.testLogging {
            // Gradle 8.x throws on emptySet() (EnumSet.copyOf limitation), Gradle 9+ allows it
            try { events = emptySet() } catch (_: IllegalArgumentException) { /* Gradle 8.x */ }
            showExceptions = false
            showStackTraces = false
            showCauses = false
        }

        testTask.doFirst {
            listener.taskStarted()
        }

        testTask.doLast {
            listener.taskFinished()
        }
    }

    private fun configureKotlinTestTask(task: Task, project: Project, aggregator: TestAggregator) {
        val handler = KotlinTestHandler(task.name, task.logger, aggregator, project.layout.buildDirectory.asFile.get())

        task.doFirst {
            handler.taskStarted()
        }

        task.doLast {
            handler.taskFinished()
        }
    }
}

// Handler for Kotlin test tasks (JS, Native, etc.)
class KotlinTestHandler(
    private val taskName: String,
    private val logger: Logger,
    private val aggregator: TestAggregator,
    private val buildDir: File
) {
    private fun log(message: String) {
        if (TestOutputStyle.isEnabled()) {
            logger.lifecycle(message + TestOutputStyle.CLEAR_TO_EOL)
        }
    }

    fun taskStarted() {
        if (!TestOutputStyle.isEnabled()) return
        log("")
        log(TestOutputStyle.LINE_SINGLE.repeat(60))
        log(" T E S T S  ($taskName)")
        log(TestOutputStyle.LINE_SINGLE.repeat(60))
    }

    fun taskFinished() {
        if (!TestOutputStyle.isEnabled()) return

        // Parse test results from XML files
        val resultsDir = File(buildDir, "test-results/$taskName")
        val results = parseTestResults(resultsDir)

        // Print per-class results
        for (classResult in results.classResults) {
            log("")
            log("${TestOutputStyle.ANSI_YELLOW}Running ${classResult.className}${TestOutputStyle.ANSI_RESET}")

            for (test in classResult.tests) {
                val (emoji, color) = when {
                    test.failed -> TestOutputStyle.FAILED to TestOutputStyle.ANSI_RED
                    test.skipped -> TestOutputStyle.SKIPPED to TestOutputStyle.ANSI_YELLOW
                    else -> TestOutputStyle.PASSED to TestOutputStyle.ANSI_GREEN
                }
                // Clean up test name - keep platform (browser/node) but remove version details
                val cleanName = test.name.replace(Regex("\\[js, (\\w+), [^]]*]")) {
                    " [${it.groupValues[1]}]"
                }.trim()
                log("$color  $emoji $cleanName${TestOutputStyle.ANSI_RESET}")
            }

            val statusColor = when {
                classResult.failures > 0 -> TestOutputStyle.ANSI_RED
                classResult.skipped > 0 -> TestOutputStyle.ANSI_YELLOW
                else -> TestOutputStyle.ANSI_GREEN
            }
            log("$statusColor  Tests run: ${classResult.tests.size}, Failures: ${classResult.failures}, Skipped: ${classResult.skipped}, Time: ${String.format("%.3f", classResult.time)}s${TestOutputStyle.ANSI_RESET}")
        }

        // Print summary
        log(TestOutputStyle.LINE_SINGLE.repeat(60))
        val statusEmoji = if (results.totalFailed == 0) TestOutputStyle.PASSED else TestOutputStyle.FAILED
        val statusColor = if (results.totalFailed == 0) TestOutputStyle.ANSI_GREEN else TestOutputStyle.ANSI_RED
        log("$statusColor$statusEmoji Tests: ${results.totalTests}, Passed: ${results.totalPassed}, Failed: ${results.totalFailed}, Skipped: ${results.totalSkipped}${TestOutputStyle.ANSI_RESET}")
        log(TestOutputStyle.LINE_SINGLE.repeat(60))
        log("")

        // Report to aggregator
        aggregator.addResults(results.totalTests, results.totalPassed, results.totalFailed, results.totalSkipped)
    }

    private fun parseTestResults(resultsDir: File): TestResultsSummary {
        val classResults = mutableListOf<ClassResult>()
        var totalTests = 0
        var totalPassed = 0
        var totalFailed = 0
        var totalSkipped = 0

        if (!resultsDir.exists()) {
            return TestResultsSummary(classResults, 0, 0, 0, 0)
        }

        val xmlFiles = resultsDir.listFiles { f -> f.name.endsWith(".xml") } ?: emptyArray()
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()

        for (xmlFile in xmlFiles) {
            try {
                val doc = builder.parse(xmlFile)
                val testsuite = doc.documentElement

                val className = testsuite.getAttribute("name")
                val tests = testsuite.getAttribute("tests").toIntOrNull() ?: 0
                val failures = (testsuite.getAttribute("failures").toIntOrNull() ?: 0) +
                               (testsuite.getAttribute("errors").toIntOrNull() ?: 0)
                val skipped = testsuite.getAttribute("skipped").toIntOrNull() ?: 0
                val time = testsuite.getAttribute("time").toDoubleOrNull() ?: 0.0

                val testCases = mutableListOf<TestCaseResult>()
                val testcaseNodes = testsuite.getElementsByTagName("testcase")
                for (i in 0 until testcaseNodes.length) {
                    val testcase = testcaseNodes.item(i)
                    val testName = testcase.attributes.getNamedItem("name")?.nodeValue ?: "unknown"
                    val hasFailure = testcase.childNodes.let { children ->
                        (0 until children.length).any {
                            val child = children.item(it)
                            child.nodeName == "failure" || child.nodeName == "error"
                        }
                    }
                    val isSkipped = testcase.childNodes.let { children ->
                        (0 until children.length).any { children.item(it).nodeName == "skipped" }
                    }
                    testCases.add(TestCaseResult(testName, hasFailure, isSkipped))
                }

                classResults.add(ClassResult(className, testCases, failures, skipped, time))
                totalTests += tests
                totalFailed += failures
                totalSkipped += skipped
                totalPassed += (tests - failures - skipped)
            } catch (e: Exception) {
                logger.warn("Failed to parse test result file ${xmlFile.name}: ${e.message}")
            }
        }

        return TestResultsSummary(classResults, totalTests, totalPassed, totalFailed, totalSkipped)
    }

    data class TestResultsSummary(
        val classResults: List<ClassResult>,
        val totalTests: Int,
        val totalPassed: Int,
        val totalFailed: Int,
        val totalSkipped: Int
    )

    data class ClassResult(
        val className: String,
        val tests: List<TestCaseResult>,
        val failures: Int,
        val skipped: Int,
        val time: Double
    )

    data class TestCaseResult(
        val name: String,
        val failed: Boolean,
        val skipped: Boolean
    )
}

// Aggregates results across all test tasks
class TestAggregator {
    private var tasksRun = 0
    private var totalTests = 0
    private var passedTests = 0
    private var failedTests = 0
    private var skippedTests = 0

    @Synchronized
    fun addResults(tests: Int, passed: Int, failed: Int, skipped: Int) {
        tasksRun++
        totalTests += tests
        passedTests += passed
        failedTests += failed
        skippedTests += skipped
    }

    @Synchronized
    fun printTotalIfMultipleTasks(logger: Logger) {
        if (!TestOutputStyle.isEnabled()) return
        if (tasksRun <= 1) return  // Don't show a summary unless the number of tasks is at least 2

        val statusEmoji = if (failedTests == 0) TestOutputStyle.PASSED else TestOutputStyle.FAILED
        val statusColor = if (failedTests == 0) TestOutputStyle.ANSI_GREEN else TestOutputStyle.ANSI_RED

        logger.lifecycle("")
        logger.lifecycle("${TestOutputStyle.LINE_DOUBLE.repeat(60)}${TestOutputStyle.CLEAR_TO_EOL}")
        logger.lifecycle(" TOTAL ($tasksRun test tasks)${TestOutputStyle.CLEAR_TO_EOL}")
        logger.lifecycle("${TestOutputStyle.LINE_DOUBLE.repeat(60)}${TestOutputStyle.CLEAR_TO_EOL}")
        logger.lifecycle("$statusColor$statusEmoji Tests: $totalTests, Passed: $passedTests, Failed: $failedTests, Skipped: $skippedTests${TestOutputStyle.ANSI_RESET}${TestOutputStyle.CLEAR_TO_EOL}")
        logger.lifecycle("${TestOutputStyle.LINE_DOUBLE.repeat(60)}${TestOutputStyle.CLEAR_TO_EOL}")
    }
}

class JvmTestReporter(
    private val taskName: String,
    private val logger: Logger,
    private val aggregator: TestAggregator
) : TestListener {

    private data class TestResultEntry(
        val descriptor: TestDescriptor,
        val result: TestResult
    )

    private class ClassState(
        val className: String,
        val results: MutableList<TestResultEntry> = mutableListOf(),
        var suiteResult: TestResult? = null
    ) {
        val isComplete get() = suiteResult != null
    }

    private val lock = Any()
    private val classStates = LinkedHashMap<String, ClassState>()
    private var activeClass: String? = null

    private var totalTests = 0
    private var passedTests = 0
    private var failedTests = 0
    private var skippedTests = 0
    private var taskWasRun = false

    private fun log(message: String) {
        if (TestOutputStyle.isEnabled()) {
            logger.lifecycle(message + TestOutputStyle.CLEAR_TO_EOL)
        }
    }

    fun taskStarted() {
        if (!TestOutputStyle.isEnabled()) return
        taskWasRun = true
        log("")
        log(TestOutputStyle.LINE_SINGLE.repeat(60))
        log(" T E S T S  ($taskName)")
        log(TestOutputStyle.LINE_SINGLE.repeat(60))
    }

    fun taskFinished() {
        if (!taskWasRun) return

        // Flush any remaining buffered classes
        synchronized(lock) {
            flushCompleted()
        }

        log(TestOutputStyle.LINE_SINGLE.repeat(60))
        val statusEmoji = if (failedTests == 0) TestOutputStyle.PASSED else TestOutputStyle.FAILED
        val statusColor = if (failedTests == 0) TestOutputStyle.ANSI_GREEN else TestOutputStyle.ANSI_RED
        log("$statusColor$statusEmoji Tests: $totalTests, Passed: $passedTests, Failed: $failedTests, Skipped: $skippedTests${TestOutputStyle.ANSI_RESET}")
        log(TestOutputStyle.LINE_SINGLE.repeat(60))
        log("")

        aggregator.addResults(totalTests, passedTests, failedTests, skippedTests)
    }

    override fun beforeSuite(suite: TestDescriptor) {
        // No output here — class header is written when we flush the buffered results
    }

    override fun afterSuite(suite: TestDescriptor, result: TestResult) {
        val className = suite.className ?: return
        if (suite.parent?.className != null) return

        synchronized(lock) {
            val state = classStates.getOrPut(className) { ClassState(className) }
            state.suiteResult = result
            flushCompleted()
        }
    }

    override fun beforeTest(testDescriptor: TestDescriptor) {
    }

    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
        totalTests++
        when (result.resultType) {
            TestResult.ResultType.SUCCESS -> passedTests++
            TestResult.ResultType.FAILURE -> failedTests++
            TestResult.ResultType.SKIPPED, null -> skippedTests++
        }

        val className = testDescriptor.className ?: return
        synchronized(lock) {
            val state = classStates.getOrPut(className) { ClassState(className) }
            state.results.add(TestResultEntry(testDescriptor, result))

            // First result we see determines the active class
            if (activeClass == null) {
                activeClass = className
            }
            flushCompleted()
        }
    }

    /** Flush the active class if complete, then pick the next most-progressed class. */
    private fun flushCompleted() {
        while (true) {
            val active = activeClass ?: break
            val state = classStates[active] ?: break
            if (!state.isComplete) break

            outputClass(state)
            classStates.remove(active)

            // Pick next: prefer completed classes, then most results buffered
            activeClass = classStates.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, ClassState>> { it.value.isComplete }
                        .thenByDescending { it.value.results.size }
                )
                .firstOrNull()?.key
        }
    }

    private fun outputClass(state: ClassState) {
        if (!TestOutputStyle.isEnabled()) return

        log("")
        log("${TestOutputStyle.ANSI_YELLOW}Running ${state.className}${TestOutputStyle.ANSI_RESET}")

        for (entry in state.results) {
            val (emoji, color) = when (entry.result.resultType) {
                TestResult.ResultType.SUCCESS -> TestOutputStyle.PASSED to TestOutputStyle.ANSI_GREEN
                TestResult.ResultType.FAILURE -> TestOutputStyle.FAILED to TestOutputStyle.ANSI_RED
                TestResult.ResultType.SKIPPED, null -> TestOutputStyle.SKIPPED to TestOutputStyle.ANSI_YELLOW
            }
            log("$color  $emoji ${entry.descriptor.displayName}${TestOutputStyle.ANSI_RESET}")

            if (entry.result.resultType == TestResult.ResultType.FAILURE) {
                for (exception in entry.result.exceptions) {
                    log("$color    ${exception.javaClass.simpleName} at ${formatLocation(exception)}${TestOutputStyle.ANSI_RESET}")
                    var cause = exception.cause
                    while (cause != null && cause !== exception) {
                        log("$color        Caused by: ${cause.javaClass.simpleName} at ${formatLocation(cause)}${TestOutputStyle.ANSI_RESET}")
                        cause = cause.cause
                    }
                }
                log("")
            }
        }

        val suiteResult = state.suiteResult!!
        val count = suiteResult.testCount
        val failed = suiteResult.failedTestCount
        val skipped = suiteResult.skippedTestCount
        val time = (suiteResult.endTime - suiteResult.startTime) / 1000.0
        val statusColor = when {
            failed > 0 -> TestOutputStyle.ANSI_RED
            skipped > 0 -> TestOutputStyle.ANSI_YELLOW
            else -> TestOutputStyle.ANSI_GREEN
        }
        log("$statusColor  Tests run: $count, Failures: $failed, Skipped: $skipped, Time: ${String.format("%.3f", time)}s${TestOutputStyle.ANSI_RESET}")
    }

    private fun formatLocation(throwable: Throwable): String {
        val element = throwable.stackTrace.firstOrNull()
        return if (element != null) "${element.fileName}:${element.lineNumber}" else "unknown"
    }
}
