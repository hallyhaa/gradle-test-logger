package org.babelserver.gradle.testlogger

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertTrue

class ParameterizedDemoTest {

    @ParameterizedTest(name = "isPrime[{0}]")
    @ValueSource(ints = [1, 2, 3, 4, 5, 6])
    fun isPrime(n: Int) {
        Thread.sleep(500)
        assertTrue(n > 0, "$n should be positive")
    }
}
