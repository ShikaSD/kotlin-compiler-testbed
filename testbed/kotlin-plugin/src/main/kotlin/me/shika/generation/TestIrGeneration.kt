package me.shika.generation

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.IrDeclarationBuilder
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockBodyImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSetValueImpl
import org.jetbrains.kotlin.ir.interpreter.toIrConst
import org.jetbrains.kotlin.ir.symbols.IrValueParameterSymbol
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.statements
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
    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        if (declaration.name.asString() != "target") {
            return super.visitSimpleFunction(declaration)
        }

        // set all parameters to assignable
        declaration.valueParameters = declaration.valueParameters.map {
            it.copyTo(declaration, isAssignable = true)
        }

        // reference new value parameters in body
        declaration.body?.transformChildrenVoid(
            object : IrElementTransformerVoid() {
                override fun visitGetValue(expression: IrGetValue): IrExpression {
                    if (expression.symbol is IrValueParameterSymbol && expression.symbol.owner.parent == declaration) {
                        return super.visitGetValue(
                            IrGetValueImpl(
                                expression.startOffset,
                                expression.endOffset,
                                expression.type,
                                declaration.valueParameters[0].symbol,
                                origin = expression.origin
                            )
                        )
                    }

                    return super.visitGetValue(expression)
                }
            }
        )


        // update body to mutate parameters
        declaration.body = declaration.body?.let { body ->
            IrBlockBodyImpl(
                body.startOffset,
                body.endOffset
            ) {
                // just set it to const string
                declaration.valueParameters.forEach {
                    statements.add(
                        IrSetValueImpl(
                            UNDEFINED_OFFSET,
                            UNDEFINED_OFFSET,
                            pluginContext.irBuiltIns.stringType,
                            it.symbol,
                            "Updated through IR".toIrConst(pluginContext.irBuiltIns.stringType),
                            null
                        )
                    )
                }
                statements.addAll(body.statements)
            }
        }

        return super.visitSimpleFunction(declaration)
    }
}