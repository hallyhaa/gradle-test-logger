package demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import static org.junit.jupiter.api.Assertions.*;

class DemoTest {

    @Test
    void testAddition() {
        assertEquals(4, 2 + 2);
    }

    @Test
    void testStringLength() {
        assertEquals(5, "Hello".length());
    }

    @Test
    void testNotNull() {
        assertNotNull("test");
    }

    @Test
    void failingTest() {
        assertNull("This will fail");
    }

    @Test
    @Disabled("Demonstrating skipped test")
    void testSkipped() {
        fail("This should be skipped");
    }
}
