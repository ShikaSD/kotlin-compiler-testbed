import kotlin.test.Test
import kotlin.test.assertEquals

class JsTest {
    @Test
    fun test() {
        assertEquals("Updated from IR", TestObject.target(""))
    }
}