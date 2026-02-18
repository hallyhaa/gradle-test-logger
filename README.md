# Babelserver Test Logger

A Gradle plugin that provides informative test output with status indicators for each test − inspired by Maven's
Surefire output.

## Features

✅ Shows pass/fail/skip status for each individual test  
✅ Displays summary with test counts and timing per test class  
✅ Adds an aggregated summary across all test tasks when running multi-project builds  
✅ Color-coded output  
✅ Makes Gradle's built-in UP-TO-DATE reporting explicit when tests don't need to be run  
✅ Supports Kotlin/JS and Kotlin/Native tests

## Skip reasons

When a test is skipped via `assumeTrue()` or `assumeFalse()`, the plugin displays the reason:

```
  ⏭️  skippedByAssumption() — Only runs on Fridays
```

For tests disabled with `@Disabled("reason")` and the other conditional annotations (`@DisabledOnOs`,
`@DisabledIfEnvironmentVariable`, etc.), the reason is **not** available. This is a Gradle limitation — JUnit Platform
passes the reason string to Gradle, but Gradle's internal test listener bridge discards it before it reaches plugin code.
We've [asked the Gradle team to address this](https://github.com/gradle/gradle/issues/5511#issuecomment-3921193408).

## Sample Output

**Single test task:**
```
────────────────────────────────────────────────────────────
 Babelserver test-logger · my-project (test)
────────────────────────────────────────────────────────────

Running demo.DemoTest
  ✅ testAddition()
  ❌ failingTest()
  ✅ testStringLength()
  ✅ testNotNull()
  ⏭️ testSkipped()
  Tests run: 5, Failures: 1, Skipped: 1, Time: 0.024s
────────────────────────────────────────────────────────────
❌ Tests: 5, Passed: 3, Failed: 1, Skipped: 1
────────────────────────────────────────────────────────────
```

**Multi-project builds with total summary:**

When multiple test tasks run (e.g., in multi-module projects or with Kotlin Multiplatform), you get an aggregated total
at the end:

```
────────────────────────────────────────────────────────────
 Babelserver test-logger · my-project (test)
────────────────────────────────────────────────────────────
...
✅ Tests: 68, Passed: 68, Failed: 0, Skipped: 0
────────────────────────────────────────────────────────────

────────────────────────────────────────────────────────────
 Babelserver test-logger · my-project (jsBrowserTest)
────────────────────────────────────────────────────────────
...
✅ Tests: 51, Passed: 51, Failed: 0, Skipped: 0
────────────────────────────────────────────────────────────

════════════════════════════════════════════════════════════
 TOTAL (3 test tasks)
════════════════════════════════════════════════════════════
✅ Tests: 323, Passed: 323, Failed: 0, Skipped: 0
════════════════════════════════════════════════════════════
```

This total summary is only shown when multiple test tasks run, giving you a quick overview that Gradle doesn't provide
by default.

## Installation

### Option 1: Per-project

Add to your `build.gradle.kts`:

```kotlin
plugins {
    id("org.babelserver.gradle.test-logger") version "+"
}
```

### Option 2: Global default for all projects

Create the file `~/.gradle/init.d/test-logger.init.gradle.kts`:

```kotlin
initscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.babelserver.gradle:test-logger:+")
    }
}

allprojects {
    apply<org.babelserver.gradle.testlogger.TestLoggerPlugin>()
}
```

This makes the plugin active for **all** Gradle projects on your machine without any extra configuration in the project
files themselves. This also keeps the plugin local to your machine − collaborators aren't affected.

## Configuration

The plugin works out of the box with no configuration. If you want to adjust its behavior, use the `testLogger` block
in your `build.gradle.kts`:

```kotlin
testLogger {
    showIndividualResults = false   // default: true
    groupParameterizedTests = false // default: true
}
```

| Setting                    | Default | Description                                                                                      |
|----------------------------|---------|--------------------------------------------------------------------------------------------------|
| `showIndividualResults`    | `true`  | When `false`, suppresses all per-class output (headers, test lines, class summaries). Only the task header and final summary are shown. |
| `groupParameterizedTests`  | `true`  | When `false`, shows each parameterized test variation on its own line instead of a group summary. |

Both settings can be overridden per-run with `-P` flags, which take priority over the DSL:

```bash
gradle test -Ptestlogger.showIndividualResults=false
gradle test -Ptestlogger.groupParameterizedTests=false
```

## Disabling the plugin

If you ever want to disable the plugin (temporarily or permanently), here's how.

With an **environment variable**:
```bash
TESTLOGGER_ENABLED=false gradle test
```

With a **Gradle property** (per invocation):
```bash
gradle test -Ptestlogger.enabled=false
```

With a property in **gradle.properties**:
```properties
testlogger.enabled=false
```

## Requirements

- Gradle 9.0+
- Java 11+

## License

MIT
