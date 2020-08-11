package me.shika.generation

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.copyBodyTo
import org.jetbrains.kotlin.backend.common.ir.copyTypeParametersFrom
import org.jetbrains.kotlin.backend.common.ir.copyValueParametersFrom
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
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
    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement =
        super.visitSimpleFunction(declaration).let {
            if (it is IrSimpleFunction && it.shouldAddParam) {
                replaceFunction(declaration)
            } else {
                it
            }
        }

    private fun replaceFunction(declaration: IrSimpleFunction): IrFunction =
        buildFun {
            updateFrom(declaration)
            returnType = declaration.returnType
            name = declaration.name
        }.also { newFunction ->
            newFunction.apply {
                copyValueParametersFrom(declaration)
                copyTypeParametersFrom(declaration)
            }
            declaration.copyBodyTo(newFunction)

            val intValueParameter = buildValueParameter {
                name = Name.identifier("\$generated")
                index = declaration.valueParameters.size
                type = pluginContext.irBuiltIns.intType
            }
            newFunction.valueParameters += intValueParameter
        }
}

class ReplaceCalls(private val pluginContext: IrPluginContext): IrElementTransformerVoid() {
    override fun visitCall(expression: IrCall): IrExpression =
        super.visitCall(expression).let {
            if (it is IrCall && it.symbol.owner.shouldAddParam) {
                transformCall(it)
            } else {
                it
            }
        }

    private fun transformCall(call: IrCall): IrCall =
        call
            .deepCopyWithSymbols()
            .also {
                it.putValueArgument(
                    it.valueArgumentsCount,
                    IrConstImpl(
                        startOffset = 0,
                        endOffset = 0,
                        type = pluginContext.irBuiltIns.intType,
                        kind = IrConstKind.Int,
                        value = 0
                    )
                )
            }
}

private val IrFunction.shouldAddParam: Boolean get() =
    name.identifier.contains("addParam")
