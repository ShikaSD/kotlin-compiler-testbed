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
        compilation.sources = listOf("prelude.kt", "interface.kt").map { it.resourceFile() }
        val result = compilation.compile()
        assertEquals(result.exitCode, OK)
    }

    private fun String.resourceFile() = SourceFile.fromPath(
        File(this@TestBedTest.javaClass.classLoader.getResource(this).file)
    )
}
