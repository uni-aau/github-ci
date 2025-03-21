package net.jamnig.testapp

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun testAddition() {
        val calculator = Coverage()
        val result = calculator.add(2, 3)
        assertEquals(5, result)
    }
}