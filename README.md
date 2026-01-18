# Gradle Test Logger Plugin

A Gradle plugin that provides informative test output with status indicators for each test − inspired by Maven's
Surefire output.

## Features

✅ Shows pass/fail/skip status for each individual test  
✅ Displays summary with test counts and timing per test class  
✅ Adds an aggregated summary across all test tasks when running multi-project builds  
✅ Color-coded output  
✅ Makes Gradle's built-in UP-TO-DATE reporting explicit when tests don't need to be run  
✅ Supports Kotlin/JS and Kotlin/Native tests

## Sample Output

**Single test task:**
```
────────────────────────────────────────────────────────────
 T E S T S  (test)
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
 T E S T S  (test)
────────────────────────────────────────────────────────────
...
✅ Tests: 68, Passed: 68, Failed: 0, Skipped: 0
────────────────────────────────────────────────────────────

────────────────────────────────────────────────────────────
 T E S T S  (jsBrowserTest)
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
