import org.junit.Assert.assertEquals
import org.junit.Test

class JunitTest {
    @Test
    fun test() {
        assertEquals("Updated through IR", TestObject.target())
    }
}