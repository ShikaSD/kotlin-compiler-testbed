package me.shika.generation

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.copyTypeParametersFrom
import org.jetbrains.kotlin.backend.common.ir.copyValueParametersFrom
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name

class ParamIrGeneration : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(AddIntParameter(pluginContext))
        moduleFragment.transformChildrenVoid(ReplaceCalls(pluginContext))
        moduleFragment.patchDeclarationParents()
    }
}

/**
 * Adds one Int param to function containing "addParam"
 */
class AddIntParameter(private val pluginContext: IrPluginContext) : IrElementTransformerVoid() {

    private val irFactory = pluginContext.irFactory

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement =
            super.visitSimpleFunction(declaration).let {
                if (it is IrSimpleFunction && it.shouldAddParam) {
                    replaceFunction(declaration)
                } else {
                    it
                }
            }

    private fun replaceFunction(declaration: IrSimpleFunction): IrFunction =
            irFactory.buildFun {
                updateFrom(declaration)
                returnType = declaration.returnType
                name = declaration.name
            }.also { newFunction ->
                newFunction.apply {
                    copyValueParametersFrom(declaration)
                    copyTypeParametersFrom(declaration)
                }

                newFunction.body = declaration.body?.deepCopyWithSymbols(newFunction)

                newFunction.addValueParameter {
                    name = Name.identifier("\$generated")
                    index = declaration.valueParameters.size
                    type = pluginContext.irBuiltIns.intType
                    isHidden = true
                }
            }
}

class ReplaceCalls(private val pluginContext: IrPluginContext) : IrElementTransformerVoid() {
    override fun visitCall(expression: IrCall): IrExpression =
            super.visitCall(expression).let {
                if (it is IrCall && it.symbol.owner.shouldAddParam) {
                    transformCall(it)
                } else {
                    it
                }
            }

    private fun transformCall(call: IrCall): IrCall {
        return call.run { IrCallImpl(startOffset, endOffset, type, symbol, 0, valueArgumentsCount + 1, origin, superQualifierSymbol) }.also {
            for (i in 0 until call.valueArgumentsCount) {
                it.putValueArgument(i, call.getValueArgument(i))
            }
            it.putValueArgument(
                    call.valueArgumentsCount,
                    IrConstImpl(
                            startOffset = 0,
                            endOffset = 0,
                            type = pluginContext.irBuiltIns.intType,
                            kind = IrConstKind.Int,
                            value = 42
                    )
            )
        }
    }
}

private val IrFunction.shouldAddParam: Boolean
    get() =
        name.identifier.contains("addParam")
