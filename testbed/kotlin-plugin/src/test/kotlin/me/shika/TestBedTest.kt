package me.shika

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import com.tschuchort.compiletesting.SourceFile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class TestBedTest {
    val compilation = KotlinCompilation().apply {
        useIR = true
        compilerPlugins = listOf(TestBedComponentRegistrar(enabled = true))
    }

    @Test
    fun `compiles source`() {
        compilation.sources = listOf("prelude.kt", "interface.kt", "class.kt").map { it.resourceFile() }
        val result = compilation.compile()
        assertEquals(result.exitCode, OK)

//        val testCls = result.classLoader.loadClass("Test")
//        val test = testCls.newInstance()
//        val method = testCls.getMethod("getMock1")
//        val mockedInterface = method.invoke(test)
//        mockedInterface::class.java.methods.forEach {
//            when (it.name) {
//                "someMethod" -> it.invoke(mockedInterface)
//                "someMethodWithParameter" -> it.invoke(mockedInterface, "string")
//            }
//        }

        val testCls = result.classLoader.loadClass("Test1")
        val test = testCls.newInstance()
        val method = testCls.getMethod("getMock2")
        val mockedInterface = method.invoke(test)
        mockedInterface::class.java.methods.forEach {
            when (it.name) {
                "someMethod" -> it.invoke(mockedInterface)
                "someMethodWithParameter" -> it.invoke(mockedInterface, "string")
            }
        }
    }

    private fun String.resourceFile() = SourceFile.fromPath(
        File(this@TestBedTest.javaClass.classLoader.getResource(this).file)
    )
}
