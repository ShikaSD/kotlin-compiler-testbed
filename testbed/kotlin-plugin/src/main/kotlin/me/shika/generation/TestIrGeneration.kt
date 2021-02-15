package me.shika.generation

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class TestIrGeneration(private val messageCollector: MessageCollector) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(SetStaticLowering(pluginContext))
        messageCollector.report(CompilerMessageSeverity.WARNING, moduleFragment.dump())
    }
}

/**
 * Adds one Int param to function containing "addParam"
 */
class SetStaticLowering(
    private val pluginContext: IrPluginContext,
) : IrElementTransformerVoid() {
    override fun visitProperty(declaration: IrProperty): IrStatement {
        if (declaration.name.asString() != "target") {
            return super.visitProperty(declaration)
        }

        declaration.backingField = declaration.backingField?.let { field ->
            val newField = pluginContext.irFactory.buildField {
                updateFrom(field)
                name = field.name
                isStatic = true
            }.also { newField ->
                newField.correspondingPropertySymbol = field.correspondingPropertySymbol
                newField.initializer = field.initializer
                newField.parent = field.parent
            }

            declaration.getter?.transformChildrenVoid(
                object : IrElementTransformerVoid() {
                    override fun visitGetField(expression: IrGetField): IrExpression {
                        return IrGetFieldImpl(
                            expression.startOffset,
                            expression.endOffset,
                            newField.symbol,
                            expression.type,
                            expression.origin,
                            expression.superQualifierSymbol
                        ).also {
                            it.receiver = expression.receiver
                        }
                    }
                },
            )

            newField
        }

        return super.visitProperty(declaration)
    }
}