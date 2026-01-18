package org.babelserver.gradle.testlogger

import kotlin.test.*

class TestLoggerPluginTest {

    @Test
    fun isKotlinTestTaskDetectsKotlinJsTest() {
        assertTrue(isKotlinTestTask("org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest"))
    }

    @Test
    fun isKotlinTestTaskDetectsKotlinJsTestSubclass() {
        assertTrue(isKotlinTestTask("org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest\$SomeInnerClass"))
    }

    @Test
    fun isKotlinTestTaskDetectsKotlinNativeTest() {
        assertTrue(isKotlinTestTask("org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest"))
    }

    @Test
    fun isKotlinTestTaskDetectsKotlinJvmTest() {
        assertTrue(isKotlinTestTask("org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest"))
    }

    @Test
    fun isKotlinTestTaskRejectsStandardGradleTest() {
        assertFalse(isKotlinTestTask("org.gradle.api.tasks.testing.Test"))
    }

    @Test
    fun isKotlinTestTaskRejectsRandomClass() {
        assertFalse(isKotlinTestTask("com.example.MyTask"))
    }

    @Test
    fun isKotlinTestTaskRejectsEmptyString() {
        assertFalse(isKotlinTestTask(""))
    }

    @Test
    fun isKotlinTestTaskRejectsSimilarButDifferentClass() {
        assertFalse(isKotlinTestTask("org.jetbrains.kotlin.gradle.tasks.KotlinCompile"))
    }

    // Helper to access the private companion object function via reflection
    private fun isKotlinTestTask(className: String): Boolean {
        val companionClass = TestLoggerPlugin::class.java.getDeclaredClasses()
            .find { it.simpleName == "Companion" }!!
        val method = companionClass.getDeclaredMethod("isKotlinTestTask", String::class.java)
        method.isAccessible = true
        val companion = TestLoggerPlugin::class.java.getDeclaredField("Companion")
        companion.isAccessible = true
        return method.invoke(companion.get(null), className) as Boolean
    }
}
