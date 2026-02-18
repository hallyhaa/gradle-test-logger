package org.babelserver.gradle.testlogger

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.assertTrue

class SkippedDemoTest {

    @Disabled("Waiting for the prime minister")
    @Test
    fun disabledWithReason() {
        assertTrue(false)
    }

    @Test
    fun skippedByAssumption() {
        assumeTrue(false, "Only runs on Fridays")
    }
}
