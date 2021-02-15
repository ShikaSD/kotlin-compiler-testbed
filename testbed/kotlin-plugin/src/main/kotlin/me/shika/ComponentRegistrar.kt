package me.shika

import me.shika.generation.TestIrGeneration
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

class TestBedComponentRegistrar @JvmOverloads constructor(
    private val enabled: Boolean = false
): ComponentRegistrar {
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(project, TestIrGeneration(configuration[CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY]!!))
    }

}
