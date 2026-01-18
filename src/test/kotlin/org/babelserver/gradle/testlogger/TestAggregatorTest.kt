package org.babelserver.gradle.testlogger

import kotlin.test.*

class TestAggregatorTest {

    @Test
    fun addResultsIncrementsTaskCount() {
        val aggregator = TestAggregator()

        aggregator.addResults(10, 8, 1, 1)
        aggregator.addResults(5, 5, 0, 0)

        // Use reflection to check internal state
        val tasksRunField = TestAggregator::class.java.getDeclaredField("tasksRun")
        tasksRunField.isAccessible = true
        assertEquals(2, tasksRunField.getInt(aggregator))
    }

    @Test
    fun addResultsAccumulatesTotals() {
        val aggregator = TestAggregator()

        aggregator.addResults(10, 8, 1, 1)
        aggregator.addResults(5, 4, 1, 0)

        val totalTestsField = TestAggregator::class.java.getDeclaredField("totalTests")
        totalTestsField.isAccessible = true
        assertEquals(15, totalTestsField.getInt(aggregator))

        val passedField = TestAggregator::class.java.getDeclaredField("passedTests")
        passedField.isAccessible = true
        assertEquals(12, passedField.getInt(aggregator))

        val failedField = TestAggregator::class.java.getDeclaredField("failedTests")
        failedField.isAccessible = true
        assertEquals(2, failedField.getInt(aggregator))

        val skippedField = TestAggregator::class.java.getDeclaredField("skippedTests")
        skippedField.isAccessible = true
        assertEquals(1, skippedField.getInt(aggregator))
    }

    @Test
    fun addResultsHandlesZeroCounts() {
        val aggregator = TestAggregator()

        aggregator.addResults(0, 0, 0, 0)

        val totalTestsField = TestAggregator::class.java.getDeclaredField("totalTests")
        totalTestsField.isAccessible = true
        assertEquals(0, totalTestsField.getInt(aggregator))
    }

    @Test
    fun addResultsHandlesLargeNumbers() {
        val aggregator = TestAggregator()

        aggregator.addResults(1000, 990, 5, 5)
        aggregator.addResults(2000, 1980, 10, 10)

        val totalTestsField = TestAggregator::class.java.getDeclaredField("totalTests")
        totalTestsField.isAccessible = true
        assertEquals(3000, totalTestsField.getInt(aggregator))
    }
}
