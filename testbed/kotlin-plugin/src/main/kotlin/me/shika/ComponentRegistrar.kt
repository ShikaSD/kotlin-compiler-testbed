package me.shika

import me.shika.generation.TestIrGeneration
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.util.dump

class TestBedComponentRegistrar @JvmOverloads constructor(
    private val enabled: Boolean = false
): ComponentRegistrar {
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        val messageCollector = configuration[CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY]!!
        IrGenerationExtension.registerExtension(project, TestIrGeneration(messageCollector))
    }

}
