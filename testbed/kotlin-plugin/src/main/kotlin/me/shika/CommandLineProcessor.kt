package me.shika

import org.jetbrains.kotlin.cli.common.toBooleanLenient
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import java.io.File

//@AutoService(CommandLineProcessor::class)
class TestBedCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "testbed"
    override val pluginOptions: Collection<AbstractCliOption> =
        listOf(
            CliOption(
                "enabled",
                "<true|false>",
                "Whether plugin is enabled",
                required = false
            )
        )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
    }

    companion object {
        val KEY_ENABLED = CompilerConfigurationKey<Boolean>("testbed.enabled")
    }
}
