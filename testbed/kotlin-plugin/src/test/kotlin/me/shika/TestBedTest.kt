package me.shika

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import com.tschuchort.compiletesting.SourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File
import java.io.Serializable
import java.lang.reflect.Method

class TestBedTest {
    val compilation = KotlinCompilation().apply {
        useIR = true
        compilerPlugins = listOf(TestBedComponentRegistrar(enabled = true))
    }

    @Test
    fun `compiles source`() {
        compilation.sources = listOf(SourceFile.fromPath("Source.kt".resourceFile()))
        val result = compilation.compile()
        assertEquals(result.exitCode, OK)
    }

    private fun String.resourceFile() = File(this@TestBedTest.javaClass.classLoader.getResource(this).file)
}
